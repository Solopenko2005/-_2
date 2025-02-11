package searchengine.services;

import lombok.Setter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import searchengine.config.IndexingSettings;
import searchengine.model.*;
import searchengine.utils.Lemmatizer;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class SiteIndexingService {
    private static final Logger logger = LoggerFactory.getLogger(SiteIndexingService.class);

    private final DatabaseService databaseService;
    private final IndexingSettings indexingSettings;
    private final AtomicBoolean indexingInProgress = new AtomicBoolean(false);
    private final ForkJoinPool pool = new ForkJoinPool();
    private final Set<String> visitedUrls = Collections.synchronizedSet(new HashSet<>());
    private static final int MAX_RETRIES = 3;
    private static final int TIMEOUT = 10000;

    public SiteIndexingService(DatabaseService databaseService, IndexingSettings indexingSettings) {
        this.databaseService = databaseService;
        this.indexingSettings = indexingSettings;
    }

    public boolean startIndexing() {
        if (indexingInProgress.compareAndSet(false, true)) {
            logger.info("Запуск индексации...");
            databaseService.clearAllData();

            for (IndexingSettings.SiteConfig siteConfig : indexingSettings.getSites()) {
                Site site = new Site();
                site.setUrl(siteConfig.getUrl());
                site.setName(siteConfig.getName());
                site.setStatus(Status.INDEXING);
                site.setStatusTime(LocalDateTime.now());
                databaseService.saveSite(site);

                pool.submit(() -> {
                    logger.info("Индексация началась для: {}", site.getUrl());
                    new SiteIndexingTask(site, site.getUrl(), 0).invoke();

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
            pool.shutdown();
            try {
                if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                pool.shutdownNow();
                logger.error("Ошибка при остановке индексации", e);
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

                Page page = new Page();
                page.setSite(site);
                page.setPath(url.replace(site.getUrl(), ""));
                page.setCode(document.connection().response().statusCode());
                page.setContent(document.outerHtml());
                databaseService.savePage(page);

                // Извлекаем и сохраняем леммы
                String text = document.body().text();
                Set<String> lemmas = Lemmatizer.extractLemmas(text);
                logger.info("Извлечено {} лемм для URL: {}", lemmas.size(), url);
                logger.info("Леммы, извлеченные из {}: {}", url, lemmas);

                for (String lemma : lemmas) {
                    if (lemma != null && !lemma.trim().isEmpty()) {
                        try {
                            databaseService.saveLemma(lemma, site);
                        } catch (DataIntegrityViolationException e) {
                            logger.warn("Лемма '{}' уже существует в базе, пропускаем", lemma);
                        } catch (Exception e) {
                            logger.error("Ошибка при сохранении леммы '{}': {}", lemma, e.getMessage(), e);
                        }
                    }
                }


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
