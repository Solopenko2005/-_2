package searchengine.services;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import searchengine.config.IndexingSettings;
import searchengine.dto.response.IndexingResponse;
import searchengine.dto.response.SearchResponse;
import searchengine.model.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import searchengine.dto.response.SearchResult;
import searchengine.services.Lemmatizer;

import javax.transaction.Transactional;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Service
public class IndexingService {

    private static final Logger logger = LoggerFactory.getLogger(IndexingService.class);

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final IndexingSettings indexingSettings;
    private final Lemmatizer lemmatizer;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    private final TransactionTemplate transactionTemplate;
    private final int delayBetweenRequests;

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final Map<String, Future<?>> siteTasks = new ConcurrentHashMap<>();
    private volatile boolean isIndexingStopped = false;

    @Autowired
    public IndexingService(SiteRepository siteRepository,
                           PageRepository pageRepository,
                           IndexingSettings indexingSettings, Lemmatizer lemmatizer, LemmaRepository lemmaRepository, IndexRepository indexRepository,
                           PlatformTransactionManager transactionManager) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.indexingSettings = indexingSettings;
        this.delayBetweenRequests = indexingSettings.getDelayBetweenRequests();
        this.lemmatizer = lemmatizer;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public IndexingResponse startIndexing() {
        if (!siteTasks.isEmpty()) {
            return new IndexingResponse(false, "Индексация уже запущена");
        }

        isIndexingStopped = false;
        List<Site> sites = getSitesFromConfig();
        for (Site site : sites) {
            Future<?> future = executorService.submit(() -> indexSite(site));
            siteTasks.put(site.getUrl(), future);
        }
        return new IndexingResponse(true);
    }

    public IndexingResponse stopIndexing() {
        if (siteTasks.isEmpty()) {
            logger.info("Попытка остановить индексацию, но она не запущена.");
            return new IndexingResponse(false, "Индексация не запущена");
        }

        logger.info("Остановка индексации...");
        isIndexingStopped = true;
        for (Map.Entry<String, Future<?>> entry : siteTasks.entrySet()) {
            String url = entry.getKey();
            Future<?> future = entry.getValue();
            future.cancel(true);
            logger.info("Задача для сайта {} остановлена.", url);
        }
        siteTasks.clear();

        // Обновляем статус для сайтов, которые ещё находятся в процессе индексации
        for (Site site : siteRepository.findAll()) {
            if (site.getStatus() == Status.INDEXING) {
                site.setStatus(Status.FAILED);
                site.setLastError("Индексация остановлена пользователем");
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);
                logger.info("Статус сайта {} изменен на FAILED.", site.getUrl());
            }
        }
        logger.info("Индексация успешно остановлена.");
        return new IndexingResponse(true);
    }

    private List<Site> getSitesFromConfig() {
        List<Site> sites = new ArrayList<>();
        for (IndexingSettings.Site siteConfig : indexingSettings.getSites()) {
            // Удаляем все существующие записи для данного URL
            List<Site> existingSites = siteRepository.findAllByUrl(siteConfig.getUrl());
            if (!existingSites.isEmpty()) {
                for (Site existingSite : existingSites) {
                    // Удаляем связанные страницы
                    transactionTemplate.execute(status -> {
                        pageRepository.deleteBySite(existingSite);
                        return null;
                    });
                    siteRepository.delete(existingSite);
                    logger.info("Удалена старая запись для сайта: {}", siteConfig.getUrl());
                }
            }
            // Создаем новую запись
            Site site = new Site();
            site.setUrl(siteConfig.getUrl());
            site.setName(siteConfig.getName());
            site.setStatus(Status.INDEXING);
            site.setStatusTime(LocalDateTime.now());
            site.setLastError(null);
            site = siteRepository.save(site);
            sites.add(site);
            logger.info("Создана новая запись для сайта: {}", site.getUrl());
        }
        return sites;
    }

    private void indexSite(Site site) {
        try {
            indexPage(site, site.getUrl(), 0); // Начинаем обход с главной страницы (глубина 0)
            site.setStatus(Status.INDEXED);
        } catch (Exception e) {
            site.setStatus(Status.FAILED);
            site.setLastError("Ошибка индексации: " + e.getMessage());
            logger.error("Ошибка индексации сайта {}: {}", site.getUrl(), e.getMessage());
        } finally {
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
        }
    }

    private void indexPage(Site site, String url, int depth) throws IOException, InterruptedException {
        if (isIndexingStopped) {
            throw new InterruptedException("Индексация остановлена");
        }
        if (depth > 10) {
            logger.warn("Достигнута максимальная глубина рекурсии для URL: {}", url);
            return;
        }
        if (pageRepository.existsBySiteAndPath(site, url)) {
            return; // Страница уже проиндексирована
        }

        Connection connection = Jsoup.connect(url)
                .userAgent(indexingSettings.getUserAgent())
                .referrer(indexingSettings.getReferer())
                .ignoreContentType(true);
        Connection.Response response = connection.execute();
        String contentType = response.contentType();

        if (contentType != null && (contentType.startsWith("text/") || contentType.contains("xml"))) {
            Document doc = response.parse();
            Page page = savePage(site, url, doc);
            // После обработки страницы обновляем время последнего изменения записи сайта
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
            indexPageContent(site, page); // Вызов метода для обработки содержимого страницы
            processLinks(site, doc, depth + 1);
        } else {
            logger.warn("Неподдерживаемый тип содержимого: {} для URL: {}", contentType, url);
        }

        TimeUnit.MILLISECONDS.sleep(delayBetweenRequests);
    }

    private Page savePage(Site site, String url, Document doc) {
        Page page = new Page();
        page.setSite(site);
        String path = url.replace(site.getUrl(), "");
        if (path.isEmpty()) {
            path = "/";
        }
        page.setPath(path);
        page.setCode(doc.connection().response().statusCode());
        page.setContent(doc.html());
        return pageRepository.save(page);
    }

    private Page savePage(Site site, String url) throws IOException {
        Connection connection = Jsoup.connect(url)
                .userAgent(indexingSettings.getUserAgent())
                .referrer(indexingSettings.getReferer())
                .ignoreContentType(true);
        Connection.Response response = connection.execute();

        if (response.statusCode() >= 400) {
            throw new IOException("HTTP ошибка: " + response.statusCode());
        }

        Document doc = response.parse();
        Page page = new Page();
        page.setSite(site);
        String path = url.replace(site.getUrl(), "");
        if (path.isEmpty()) {
            path = "/";
        }
        page.setPath(path);
        page.setCode(response.statusCode());
        page.setContent(doc.html());
        return pageRepository.save(page);
    }

    private void processLinks(Site site, Document doc, int depth) throws InterruptedException, IOException {
        Elements links = doc.select("a[href]");
        for (Element link : links) {
            if (isIndexingStopped) {
                throw new InterruptedException("Индексация остановлена");
            }
            String nextUrl = link.absUrl("href");
            if (nextUrl.startsWith(site.getUrl())) {
                indexPage(site, nextUrl, depth);
            }
        }
    }

    public IndexingResponse indexPage(String url) {
        try {
            // Проверка, принадлежит ли страница одному из сайтов из конфигурации
            boolean isUrlAllowed = false;
            for (IndexingSettings.Site siteConfig : indexingSettings.getSites()) {
                if (url.startsWith(siteConfig.getUrl())) {
                    isUrlAllowed = true;
                    break;
                }
            }
            if (!isUrlAllowed) {
                return new IndexingResponse(false, "Данная страница не принадлежит сайтам, указанным в конфигурации");
            }

            // Поиск или создание сайта
            Site site = findOrCreateSite(url);

            // Удаление старой информации о странице, если она уже была проиндексирована
            String path = url.replace(site.getUrl(), "");
            if (path.isEmpty()) {
                path = "/";
            }
            Page existingPage = pageRepository.findBySiteAndPath(site, path);
            if (existingPage != null) {
                deletePageInfo(existingPage);
            }

            // Сохранение страницы
            Page page = savePage(site, url);
            if (page.getCode() >= 400) {
                return new IndexingResponse(false, "Страница не индексируется из-за ошибки HTTP: " + page.getCode());
            }

            // Индексация содержимого страницы
            indexPageContent(site, page);

            return new IndexingResponse(true);
        } catch (Exception e) {
            logger.error("Ошибка индексации страницы: {}", e.getMessage(), e);
            return new IndexingResponse(false, "Ошибка индексации: " + e.getMessage());
        }
    }

    @Transactional
    private void deletePageInfo(Page page) {
        List<Index> indexes = indexRepository.findByPage(page);
        for (Index index : indexes) {
            // Проверяем, существует ли индекс перед удалением
            if (indexRepository.existsById(index.getId())) {
                indexRepository.delete(index);
            } else {
                logger.warn("Индекс с id {} уже удален или не существует.", index.getId());
            }

            Lemma lemma = index.getLemma();
            lemma.setFrequency(lemma.getFrequency() - 1);
            if (lemma.getFrequency() == 0) {
                lemmaRepository.delete(lemma);
            } else {
                lemmaRepository.save(lemma);
            }
        }
        pageRepository.delete(page);
    }

    private Site findOrCreateSite(String url) {
        String siteUrl = url.split("/")[2]; // Извлекаем домен из URL
        Site site = siteRepository.findByUrl(siteUrl).orElseGet(() -> {
            Site newSite = new Site();
            newSite.setUrl(siteUrl);
            newSite.setName(siteUrl);
            newSite.setStatus(Status.INDEXED);
            newSite.setStatusTime(LocalDateTime.now());
            return siteRepository.save(newSite);
        });
        return site;
    }


    private void indexPageContent(Site site, Page page) {
        String text = Jsoup.parse(page.getContent()).text();

        // Лемматизация текста
        Map<String, Integer> lemmas;
        try {
            lemmas = lemmatizer.getLemmas(text);
        } catch (Exception e) {
            logger.error("Ошибка лемматизации для страницы {}: {}", page.getPath(), e.getMessage());
            return;
        }

        // Сохранение лемм и индексов
        for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
            String lemmaText = entry.getKey();
            int rank = entry.getValue();

            // Поиск или создание леммы
            Lemma lemma = lemmaRepository.findOneByLemmaAndSite(lemmaText, site)
                    .orElseGet(() -> {
                        Lemma newLemma = new Lemma();
                        newLemma.setSite(site);
                        newLemma.setLemma(lemmaText);
                        newLemma.setFrequency(0);
                        return lemmaRepository.save(newLemma);
                    });

            // Увеличиваем частоту леммы
            lemma.setFrequency(lemma.getFrequency() + 1);
            lemmaRepository.save(lemma);

            // Создание и сохранение индекса
            Index index = new Index();
            index.setPage(page);
            index.setLemma(lemma);
            index.setRank((float) rank);
            indexRepository.save(index);

            logger.info("Лемма '{}' добавлена для страницы {}", lemmaText, page.getPath());
        }
    }
}