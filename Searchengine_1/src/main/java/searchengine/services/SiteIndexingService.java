package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.IndexingSettings;
import searchengine.dto.response.IndexingResponse;
import searchengine.model.*;
import searchengine.repository.SiteRepository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SiteIndexingService {

    @PersistenceContext
    private final EntityManager entityManager;
    private final Lemmatizer lemmatizer;
    private final DatabaseService databaseService;
    private final IndexingSettings indexingSettings;
    private final SiteRepository siteRepository;
    private final PageProcessor pageProcessor;

    private static final Logger logger = LoggerFactory.getLogger(SiteIndexingService.class);
    private final AtomicBoolean indexingInProgress = new AtomicBoolean(false);
    private ForkJoinPool pool;
    private final Set<String> visitedUrls = ConcurrentHashMap.newKeySet();
    private static final int MAX_RETRIES = 3;
    private static final int TIMEOUT = 10000;
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    public ResponseEntity<Map<String, Object>> startIndexing() {
        try {
            if (!indexingInProgress.compareAndSet(false, true)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "result", false,
                        "error", "Индексация уже запущена"
                ));
            }

            databaseService.truncateAllTables();
            visitedUrls.clear();

            pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());

            for (IndexingSettings.SiteConfig siteConfig : indexingSettings.getSites()) {
                if (stopRequested.get()) break;
                CompletableFuture.runAsync(() -> processSite(siteConfig), pool);
            }

            return ResponseEntity.ok(Map.of(
                    "result", true,
                    "message", "Индексация запущена"
            ));
        } catch (Exception e) {
            logger.error("Ошибка при запуске индексации", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "result", false,
                    "error", "Internal Server Error"
            ));
        }
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

    public ResponseEntity<IndexingResponse> stopIndexing() {
        logger.info("Запрос на остановку индексации...");
        try {
            if (!indexingInProgress.get()) {
                return ResponseEntity.badRequest().body(
                        new IndexingResponse(false, "Индексация не запущена")
                );
            }

            stopRequested.set(true);
            pool.shutdown();

            new Thread(() -> {
                try {
                    if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                        logger.warn("Не все задачи завершились за отведенное время");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    indexingInProgress.set(false);
                    updateSiteStatusesOnStop();
                    logger.info("Индексация остановлена");
                }
            }).start();

            return ResponseEntity.ok(
                    new IndexingResponse(true, "Индексация остановлена")
            );
        } catch (Exception e) {
            logger.error("Ошибка при остановке индексации", e);
            return ResponseEntity.internalServerError().body(
                    new IndexingResponse(false, "Ошибка при остановке индексации")
            );
        }
    }
    public ResponseEntity<Map<String, Object>> indexPage(String url) {
        try {
            if (indexingInProgress.get()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "result", false,
                        "error", "Индексация уже запущена"
                ));
            }

            if (!isValidUrl(url)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "result", false,
                        "error", "Некорректный URL"
                ));
            }

            Site site = getSiteByUrl(url);
            if (site == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "result", false,
                        "error", "Страница не принадлежит ни одному из разрешенных сайтов"
                ));
            }

            indexingInProgress.set(true);
            try {
                pageProcessor.deletePageInfoIfExists(site, url);
                pageProcessor.indexPage(site, url, 0);
                return ResponseEntity.ok(Map.of("result", true));
            } finally {
                indexingInProgress.set(false);
            }

        } catch (Exception e) {
            logger.error("Ошибка индексации страницы", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "result", false,
                    "error", "Ошибка индексации: " + e.getMessage()
            ));
        }
    }

    private Site getSiteByUrl(String url) {
        return indexingSettings.getSites().stream()
                .map(siteConfig -> siteRepository.findByUrl(siteConfig.getUrl())
                        .orElseGet(() -> {
                            Site newSite = new Site();
                            newSite.setUrl(siteConfig.getUrl());
                            newSite.setName(siteConfig.getName());
                            newSite.setStatus(Status.INDEXING);
                            return newSite;
                        }))
                .filter(s -> url.startsWith(s.getUrl()))
                .findFirst()
                .orElse(null);
    }

    private boolean isValidUrl(String url) {
        try {
            new URL(url);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    private void updateSiteStatusesOnStop() {
        for (IndexingSettings.SiteConfig siteConfig : indexingSettings.getSites()) {
            Site site = siteRepository.findByUrl(siteConfig.getUrl()).orElse(null);
            if (site != null && site.getStatus() == Status.INDEXING) {
                site.setStatus(Status.FAILED);
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
                if (stopRequested.get()) {
                    throw new IOException("Задача прервана");
                }

                Thread.sleep(500);
                long startTime = System.currentTimeMillis();
                Connection.Response response = Jsoup.connect(url)
                        .userAgent("HeliontSearchBot")
                        .timeout(TIMEOUT)
                        .execute();

                logger.info("Запрос к {} выполнен за {} мс", url, System.currentTimeMillis() - startTime);

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
            try {
                if (stopRequested.get() || !visitedUrls.add(url)) {
                    return null;
                }

                Document document = fetchDocumentWithRetries(url);
                if (document == null || stopRequested.get()) {
                    return null;
                }

                savePageAndLemmas(site, url, document);

                if (depth < 10) {
                    Elements links = document.select("a[href]");
                    List<SiteIndexingTask> subTasks = links.stream()
                            .map(link -> link.absUrl("href"))
                            .filter(this::isValidUrl)
                            .map(link -> new SiteIndexingTask(site, link, depth + 1))
                            .collect(Collectors.toList());

                    invokeAll(subTasks);
                }
            } catch (CancellationException e) {
                logger.warn("Задача была отменена для URL: {}", url);
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
            logger.info("Индексация прервана пользователем для URL: {}", url);
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