package searchengine.services;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.IndexingSettings;
import searchengine.model.*;
import searchengine.repository.SiteRepository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
public class SiteIndexingService {
    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private Lemmatizer lemmatizer;

    private static final Logger logger = LoggerFactory.getLogger(SiteIndexingService.class);

    @Autowired
    private DatabaseService databaseService;

    @Autowired
    private IndexingSettings indexingSettings;

    @Autowired
    private SiteRepository siteRepository;

    private final AtomicBoolean indexingInProgress = new AtomicBoolean(false);
    private ForkJoinPool pool;
    private final Set<String> visitedUrls = ConcurrentHashMap.newKeySet();
    private static final int MAX_RETRIES = 3;
    private static final int TIMEOUT = 10000;
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private final List<Future<?>> indexingTasks = new CopyOnWriteArrayList<>();

    public Map<String, Object> startIndexing() {
        if (!indexingInProgress.compareAndSet(false, true)) {
            return Map.of("result", false, "error", "Индексация уже запущена");
        }

        databaseService.truncateAllTables();
        visitedUrls.clear();

        pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());

        for (IndexingSettings.SiteConfig siteConfig : indexingSettings.getSites()) {
            if (stopRequested.get()) break;
            CompletableFuture.runAsync(() -> processSite(siteConfig), pool);
        }

        return Map.of("result", true, "message", "Индексация запущена");
    }

    private void processSite(IndexingSettings.SiteConfig siteConfig) {
        Site site = getOrCreateSite(siteConfig);
        if (stopRequested.get()) return;

        try {
            site.setStatus(Status.INDEXING);
            site.setStatusTime(LocalDateTime.now());
            databaseService.saveSite(site);

            new SiteIndexingTask(site, site.getUrl(), 0).fork();
        } catch (Exception e) {
            handleSiteError(site, e);
        }
    }

    private Site getOrCreateSite(IndexingSettings.SiteConfig siteConfig) {
        return siteRepository.findByUrl(siteConfig.getUrl())
                .orElseGet(() -> {
                    Site newSite = new Site();
                    newSite.setUrl(siteConfig.getUrl());
                    newSite.setName(siteConfig.getName());
                    newSite.setStatus(Status.INDEXING);
                    newSite.setStatusTime(LocalDateTime.now());
                    return newSite;
                });
    }

    private void handleSiteError(Site site, Exception e) {
        site.setStatus(Status.FAILED);
        site.setLastError(e.getMessage());
        site.setStatusTime(LocalDateTime.now());
        databaseService.saveSite(site);
        logger.error("Ошибка индексации {}: {}", site.getUrl(), e.getMessage(), e);
    }

    public boolean stopIndexing() {
        if (!indexingInProgress.get()) return false;

        stopRequested.set(true); // Устанавливаем флаг остановки
        pool.shutdownNow(); // Принудительно останавливаем все потоки

        new Thread(() -> {
            try {
                if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    pool.shutdownNow().forEach(task -> {
                        if (task instanceof Future) {
                            ((Future<?>) task).cancel(true); // Отменяем все задачи
                        }
                    });
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                indexingInProgress.set(false);
                updateSiteStatusesOnStop(); // Обновляем статусы сайтов
                logger.info("Индексация остановлена");
            }
        }).start();

        return true;
    }

    private void updateSiteStatusesOnStop() {
        for (IndexingSettings.SiteConfig siteConfig : indexingSettings.getSites()) {
            Site site = siteRepository.findByUrl(siteConfig.getUrl()).orElse(null);
            if (site != null && site.getStatus() == Status.INDEXING) {
                site.setStatus(Status.INDEXED);
                site.setLastError("Индексация остановлена пользователем");
                site.setStatusTime(LocalDateTime.now());
                databaseService.saveSite(site);
            }
        }
    }

    private Document fetchDocumentWithRetries(String url) throws IOException {
        int retries = 0;
        while (retries < MAX_RETRIES && !stopRequested.get()) {
            try {
                Thread.sleep(500); // Задержка между запросами
                Connection.Response response = Jsoup.connect(url)
                        .userAgent("HeliontSearchBot")
                        .timeout(TIMEOUT)
                        .execute();

                if (response.statusCode() >= 400) {
                    logger.warn("HTTP-ошибка {}: {}", response.statusCode(), url);
                    return null;
                }

                return response.parse();
            } catch (SocketTimeoutException e) {
                retries++;
                logger.warn("Таймаут подключения к {}. Попытка {}/{}", url, retries, MAX_RETRIES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Задача прервана", e);
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
            if (stopRequested.get() || !visitedUrls.add(url)) {
                return null;
            }

            try {
                Document document = fetchDocumentWithRetries(url);
                if (stopRequested.get()) return null;

                savePageAndLemmas(site, url, document);

                // Увеличиваем глубину обхода (например, до 10)
                if (depth < 10) {
                    Elements links = document.select("a[href]");
                    List<SiteIndexingTask> subTasks = links.stream()
                            .map(link -> link.absUrl("href"))
                            .filter(this::isValidUrl)
                            .map(link -> new SiteIndexingTask(site, link, depth + 1))
                            .collect(Collectors.toList());

                    invokeAll(subTasks);
                }
            } catch (Exception e) {
                logger.error("Ошибка обработки {}: {}", url, e.getMessage(), e);
            } finally {
                entityManager.clear();
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

        @Transactional(rollbackFor = Exception.class, timeout = 30)
        private void savePageAndLemmas(Site site, String url, Document document) {
            if (stopRequested.get()) {
                throw new RuntimeException("Индексация прервана");
            }

            Page page = createPage(site, url, document);
            String content = document.body().text();

            Map<String, Integer> lemmaMap = lemmatizer.extractLemmasWithRank(content);
            databaseService.savePage(page);

            lemmaMap.forEach((lemmaText, rank) -> {
                if (stopRequested.get()) return;
                Lemma savedLemma = databaseService.saveLemma(lemmaText, site);
                if (savedLemma != null) {
                    saveSearchIndex(page, savedLemma, rank);
                }
            });
        }

        private Page createPage(Site site, String url, Document document) {
            Page page = new Page();
            page.setSite(site);
            page.setPath(url.replace(site.getUrl(), ""));
            page.setCode(document.connection().response().statusCode());
            page.setContent(document.outerHtml());
            return page;
        }

        private void saveSearchIndex(Page page, Lemma lemma, int rank) {
            SearchIndex index = new SearchIndex();
            index.setPage(page);
            index.setLemma(lemma);
            index.setRanking(rank);
            databaseService.saveSearchIndex(index);
        }
    }