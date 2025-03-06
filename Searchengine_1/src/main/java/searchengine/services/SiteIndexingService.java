package searchengine.services;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.IndexingSettings;
import searchengine.model.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.SiteRepository;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class SiteIndexingService {
    private static final Logger logger = LoggerFactory.getLogger(SiteIndexingService.class);

    private final DatabaseService databaseService;
    private final IndexingSettings indexingSettings;
    private final AtomicBoolean indexingInProgress = new AtomicBoolean(false);
    private ForkJoinPool pool;
    private final Set<String> visitedUrls = Collections.synchronizedSet(new HashSet<>());
    private static final int MAX_RETRIES = 3;
    private static final int TIMEOUT = 10000;
    private final SiteRepository siteRepository;
    private final IndexRepository searchIndexRepository;

    public SiteIndexingService(DatabaseService databaseService, IndexingSettings indexingSettings,
                               SiteRepository siteRepository, IndexRepository searchIndexRepository) {
        this.databaseService = databaseService;
        this.indexingSettings = indexingSettings;
        this.siteRepository = siteRepository;
        this.searchIndexRepository = searchIndexRepository;
    }

    @Transactional
    public boolean startIndexing() {
        if (indexingInProgress.compareAndSet(false, true)) {
            logger.info("Запуск индексации...");

            // Очистка данных перед началом индексации
            databaseService.clearAllData();

            pool = new ForkJoinPool(); // Создаем новый пул потоков

            for (IndexingSettings.SiteConfig siteConfig : indexingSettings.getSites()) {
                // Проверяем, существует ли сайт в базе данных
                Optional<Site> existingSite = siteRepository.findByUrl(siteConfig.getUrl());

                Site site;
                if (existingSite.isPresent()) {
                    // Если сайт уже существует, обновляем его статус
                    site = existingSite.get();
                    site.setStatus(Status.INDEXING);
                    site.setStatusTime(LocalDateTime.now());
                    site.setLastError(null);
                } else {
                    // Если сайта нет, создаем новый
                    site = new Site();
                    site.setUrl(siteConfig.getUrl());
                    site.setName(siteConfig.getName());
                    site.setStatus(Status.INDEXING);
                    site.setStatusTime(LocalDateTime.now());
                }

                // Сохраняем сайт в базу данных
                databaseService.saveSite(site);

                // Запускаем задачу индексации для сайта
                pool.submit(() -> {
                    logger.info("Индексация началась для: {}", site.getUrl());
                    new SiteIndexingTask(site, site.getUrl(), 0).invoke();

                    // Обновляем статус сайта после завершения индексации
                    site.setStatus(Status.INDEXED);
                    site.setStatusTime(LocalDateTime.now());
                    databaseService.saveSite(site);
                    logger.info("Индексация завершена для: {}", site.getUrl());
                });
            }
            return true;
        }
        return false;
    }

    public boolean stopIndexing() {
        if (indexingInProgress.compareAndSet(true, false)) {
            logger.info("Остановка индексации...");
            if (pool != null) {
                pool.shutdownNow(); // Принудительно останавливаем все задачи
                pool = null;
            }
            logger.info("Индексация успешно остановлена.");
            return true;
        }
        return false;
    }

    private Document fetchDocumentWithRetries(String url) throws IOException {
        int retries = 0;
        while (retries < MAX_RETRIES) {
            try {
                return Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .timeout(TIMEOUT)
                        .get();
            } catch (SocketTimeoutException e) {
                retries++;
                logger.warn("Ошибка подключения к {}. Попытка {}/{}", url, retries, MAX_RETRIES);
            }
        }
        throw new IOException("Не удалось загрузить страницу после " + MAX_RETRIES + " попыток: " + url);
    }

    private class SiteIndexingTask extends RecursiveTask<Void> {
        private final Site site;
        private final String url;
        private final int depth;

        public SiteIndexingTask(Site site, String url, int depth) {
            this.site = site;
            this.url = url;
            this.depth = depth;
        }

        @Override
        protected Void compute() {
            logger.info("Выполняется compute() для URL: {}", url);
            if (!visitedUrls.add(url)) {
                return null;
            }

            try {
                Document document = fetchDocumentWithRetries(url);
                if (document == null) return null;

                // Сохраняем страницу
                Page page = savePage(site, url, document);

                // Извлекаем и сохраняем леммы
                String text = document.body().text();
                Map<String, Integer> lemmasWithRank = Lemmatizer.extractLemmasWithRank(text);
                logger.info("Извлечено {} лемм для URL: {}", lemmasWithRank.size(), url);
                logger.info("Леммы, извлеченные из {}: {}", url, lemmasWithRank.keySet());

                saveLemmasAndIndexes(site, page, lemmasWithRank);

                // Обработка ссылок
                Elements links = document.select("a[href]");
                List<SiteIndexingTask> subTasks = new ArrayList<>();

                for (String link : links.eachAttr("abs:href")) {
                    if (isValidUrl(link)) {
                        subTasks.add(new SiteIndexingTask(site, link, depth + 1));
                    }
                }

                invokeAll(subTasks);
                logger.info("Обработано {} страниц на глубине {}", subTasks.size(), depth);
            } catch (Exception e) {
                site.setStatus(Status.FAILED);
                site.setStatusTime(LocalDateTime.now());
                String errorMessage = e.getMessage();
                site.setLastError(errorMessage);
                databaseService.updateSiteStatus(site, errorMessage);
                logger.error("Ошибка при обработке URL {}: {}", url, errorMessage, e);
            }
            return null;
        }

        @Transactional
        private Page savePage(Site site, String url, Document document) {
            Page page = new Page();
            page.setSite(site);
            page.setPath(url.replace(site.getUrl(), ""));
            page.setCode(document.connection().response().statusCode());
            page.setContent(document.outerHtml());
            return databaseService.savePage(page);
        }

        @Transactional
        private void saveLemmasAndIndexes(Site site, Page page, Map<String, Integer> lemmasWithRank) {
            for (Map.Entry<String, Integer> entry : lemmasWithRank.entrySet()) {
                String lemmaText = entry.getKey();
                int rank = entry.getValue();

                if (lemmaText != null && !lemmaText.trim().isEmpty()) {
                    try {
                        // Сохраняем лемму
                        Lemma lemma = databaseService.saveLemma(lemmaText, site);
                        if (lemma != null) { // Проверяем, что лемма была успешно сохранена
                            // Создаем и сохраняем индекс (связь между страницей, леммой и рангом)
                            SearchIndex index = new SearchIndex();
                            index.setPage(page);
                            index.setLemma(lemma);
                            index.setRanking(rank);

                            // Сохраняем индекс в базу данных
                            databaseService.saveSearchIndex(index);
                            logger.info("Индекс сохранен для страницы {} и леммы {}", page.getPath(), lemmaText);
                        } else {
                            logger.warn("Лемма '{}' не была сохранена, индекс не создан", lemmaText);
                        }
                    } catch (DataIntegrityViolationException e) {
                        logger.warn("Лемма '{}' уже существует в базе, пропускаем", lemmaText);
                    } catch (Exception e) {
                        logger.error("Ошибка при сохранении леммы '{}': {}", lemmaText, e.getMessage(), e);
                    }
                }
            }
        }

        private boolean isValidUrl(String url) {
            return url.startsWith(site.getUrl()) &&
                    !visitedUrls.contains(url) &&
                    !url.contains("#") &&
                    !url.endsWith(".jpg") &&
                    !url.endsWith(".png") &&
                    !url.endsWith(".pdf");
        }
    }
}