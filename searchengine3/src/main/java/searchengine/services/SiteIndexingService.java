package searchengine.services;

import lombok.Setter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.IndexingSettings;
import searchengine.model.*;
import searchengine.utils.Lemmatizer;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
public class SiteIndexingService {

    @Setter
    @Autowired
    private DatabaseService databaseService;

    @Setter
    @Autowired
    private IndexingSettings indexingSettings;

    private final AtomicBoolean indexingInProgress = new AtomicBoolean(false);
    private final ForkJoinPool pool = new ForkJoinPool();
    private final Set<String> visitedUrls = Collections.synchronizedSet(new HashSet<>());
    private static final int MAX_RETRIES = 3;
    private static final int TIMEOUT = 10000;

    public boolean startIndexing() {
        if (indexingInProgress.compareAndSet(false, true)) {
            clearSiteData();

            for (IndexingSettings.SiteConfig siteConfig : indexingSettings.getSites()) {
                Site site = new Site();
                site.setUrl(siteConfig.getUrl());
                site.setName(siteConfig.getName());
                site.setStatus(Status.INDEXING);
                site.setStatusTime(LocalDateTime.now());
                databaseService.saveSite(site);

                pool.submit(() -> {
                    new SiteIndexingTask(site, siteConfig.getUrl(), 0, visitedUrls).fork();
                    site.setStatus(Status.INDEXED);
                    site.setStatusTime(LocalDateTime.now());
                    databaseService.saveSite(site);
                });
            }
            return true;
        }
        return false;
    }

    public boolean stopIndexing() {
        if (indexingInProgress.compareAndSet(true, false)) {
            pool.shutdown(); // Позволяет завершить текущие задачи
            try {
                if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                    pool.shutdownNow(); // Если за 10 сек не завершилось — принудительное завершение
                }
            } catch (InterruptedException e) {
                pool.shutdownNow();
            }
            return true;
        }
        return false;
    }


    private void clearSiteData() {
        databaseService.clearAllData();
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
                System.out.println("Ошибка подключения к " + url + ". Попытка " + retries);
            }
        }
        throw new IOException("Не удалось загрузить страницу после " + MAX_RETRIES + " попыток: " + url);
    }


    private class SiteIndexingTask extends RecursiveTask<Void> {
        private final Site site;
        private final String url;
        private final int depth;
        private final Set<String> visitedUrls;

        public SiteIndexingTask(Site site, String url, int depth, Set<String> visitedUrls) {
            this.site = site;
            this.url = url;
            this.depth = depth;
            this.visitedUrls = visitedUrls;
        }

        @Override
        protected Void compute() {
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

                // Обрабатываем текст страницы и сохраняем леммы
                String text = document.body().text();
                Set<String> lemmas = Lemmatizer.extractLemmas(text);
                System.out.println("Извлечённые леммы: " + lemmas);

                for (String lemma : lemmas) {
                    databaseService.saveLemma(lemma, site);
                }

                Elements links = document.select("a[href]");
                List<SiteIndexingTask> subTasks = links.stream()
                        .map(link -> link.absUrl("href"))
                        .filter(this::isValidUrl)
                        .map(link -> new SiteIndexingTask(site, link, depth + 1, visitedUrls))
                        .collect(Collectors.toList());

                invokeAll(subTasks);
                System.out.println("Задача завершена успешно!");
            } catch (Exception e) {
                site.setStatus(Status.FAILED);
                site.setStatusTime(LocalDateTime.now());

                String errorMessage = e.getMessage(); // Получаем текст ошибки
                site.setLastError(errorMessage); // Записываем в объект site

                databaseService.updateSiteStatus(site, errorMessage); // Вызываем метод обновления статуса

                e.printStackTrace(); // Логируем ошибку
            }

            return null;
        }



            public boolean isValidUrl(String url) {
            return url.startsWith(site.getUrl()) &&
                    !visitedUrls.contains(url) &&
                    !url.contains("#") &&
                    !url.endsWith(".jpg") &&
                    !url.endsWith(".png") &&
                    !url.endsWith(".pdf");
        }
    }
}
