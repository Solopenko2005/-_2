package searchengine.services;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.IndexingState;
import searchengine.model.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.transaction.Transactional;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


@Service
public class PageProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PageProcessor.class);

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private LemmaRepository lemmaRepository;

    @Autowired
    private IndexRepository indexRepository;

    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private Lemmatizer lemmatizer;
    private final IndexingState indexingState;

    private final AtomicBoolean isIndexingStopped = new AtomicBoolean(false);
    private final int delayBetweenRequests = 500; // Задержка между запросами в миллисекундах

    public PageProcessor(IndexingState indexingState) {
        this.indexingState = indexingState;
    }

    /**
     * Метод для индексации сайта
     *
     * @param site Сайт для индексации
     */
    public void indexSite(Site site) {
        try {
            // Удаление старых данных сайта
            deleteSiteData(site);

            // Начало индексации с главной страницы
            indexPage(site, site.getUrl(), 0);

            // Обновление статуса сайта
            site.setStatus(Status.INDEXED);
            site.setLastError(null);
        } catch (Exception e) {
            site.setStatus(Status.FAILED);
            site.setLastError("Ошибка индексации: " + e.getMessage());
            logger.error("Ошибка индексации сайта {}: {}", site.getUrl(), e.getMessage());
        } finally {
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
        }
    }

    /**
     * Метод для индексации отдельной страницы
     *
     * @param site  Сайт
     * @param url   URL страницы
     * @param depth Глубина рекурсии
     */
    public void indexPage(Site site, String url, int depth) throws IOException, InterruptedException {
        if (isIndexingStopped.get()) {
            throw new InterruptedException("Индексация остановлена");
        }

        try {
            String path = url.replace(site.getUrl(), "");
            if (path.isEmpty()) path = "/";

            // Проверка существования страницы
            Optional<Page> existingPage = pageRepository.findBySiteAndPath(site.getId(), path);
            existingPage.ifPresent(this::deletePageInfo);

            // Проверка уникальности URL
            if (pageRepository.existsBySiteAndPath(site, url.replace(site.getUrl(), ""))) {
                logger.warn("Страница уже существует: {}", url);
                return;
            }

            Connection connection = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .referrer("https://www.google.com")
                    .ignoreContentType(true);

            Connection.Response response = connection.execute();
            int statusCode = response.statusCode();

            if (statusCode >= 400) {
                logger.warn("Ошибка HTTP {}: {}", statusCode, url);
                return;
            }

            String contentType = response.contentType();
            if (contentType == null || !(contentType.startsWith("text/") || contentType.contains("xml"))) {
                logger.warn("Неподдерживаемый тип содержимого: {}", contentType);
                return;
            }

            Document doc = response.parse();
            Page page = savePage(site, url, doc);
            indexPageContent(site, page);
            processLinks(site, doc, depth);

        } catch (Exception e) {
            logger.error("Ошибка при индексации страницы {}: {}", url, e.getMessage());
            throw e; // Пробрасываем исключение дальше, чтобы оно могло быть обработано на уровне выше
        }
    }
    /**
     * Метод для обработки ссылок на странице
     *
     * @param site  Сайт
     * @param doc   HTML-документ
     * @param depth Глубина рекурсии
     */
    private void processLinks(Site site, Document doc, int depth) throws InterruptedException, IOException {
        Elements links = doc.select("a[href]");
        for (Element link : links) {
            if (isIndexingStopped.get()) {
                throw new InterruptedException("Индексация остановлена");
            }
            String nextUrl = link.absUrl("href");
            if (nextUrl.startsWith(site.getUrl())) {
                indexPage(site, nextUrl, depth);
            }
        }
    }

    /**
     * Метод для сохранения страницы в базу данных
     *
     * @param site Сайт
     * @param url  URL страницы
     * @param doc  HTML-документ
     * @return Сохраненная страница
     */
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

    /**
     * Метод для индексации содержимого страницы
     *
     * @param site Сайт
     * @param page Страница
     */
    private void indexPageContent(Site site, Page page) {
        String text = Jsoup.parse(page.getContent()).text();
        // Вызываем через экземпляр
        Map<String, Integer> lemmas = lemmatizer.extractLemmasWithRank(text);

        // Пакетная вставка с проверкой прерывания
        List<Lemma> lemmaList = new ArrayList<>();
        List<SearchIndex> indexList = new ArrayList<>();

        lemmas.forEach((lemmaText, rank) -> {
            if (indexingState.isStopRequested()) return;
            Lemma lemma = findOrCreateLemma(site, lemmaText);
            lemma.setFrequency(lemma.getFrequency() + 1);
            lemmaList.add(lemma);

            SearchIndex index = new SearchIndex();
            index.setPage(page);
            index.setLemma(lemma);
            index.setRanking(rank);
            indexList.add(index);
        });

        lemmaRepository.saveAll(lemmaList);
        indexRepository.saveAll(indexList);
    }

    /**
     * Поиск или создание леммы
     *
     * @param site      Сайт
     * @param lemmaText Текст леммы
     * @return Лемма
     */
    // В PageProcessor.java
    @Transactional
    private Lemma findOrCreateLemma(Site site, String lemmaText) {
        return lemmaRepository.findUniqueLemma(lemmaText, site.getId())
                .orElseGet(() -> {
                    Lemma newLemma = new Lemma();
                    newLemma.setSite(site);
                    newLemma.setLemma(lemmaText);
                    newLemma.setFrequency(0);
                    return lemmaRepository.save(newLemma);
                });
    }
    /**
     * Метод для преобразования текста в массив слов
     */
    private String[] convertingTextToArray(String text) {
        return text.toLowerCase().replaceAll("[^а-яё]", " ").split("\\s+");
    }

    /**
     * Проверка соответствия слова стоп-словам или частицам
     */
    private boolean checkComplianceWordToParticlesNames(List<String> wordBaseForms) {
        for (String form : wordBaseForms) {
            if (form.contains("МЕЖД") || form.contains("ПРЕДЛ") || form.contains("ЧАСТ")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Удаляет информацию о странице из таблиц page, lemma и index.
     *
     * @param page Страница
     */
    @Transactional
    public void deletePageInfo(Page page) {
        // Получаем все индексы, связанные с данной страницей
        List<SearchIndex> indexes = indexRepository.findByPage(page);

        // Удаляем индексы и обновляем частоту лемм
        for (SearchIndex index : indexes) {
            Lemma lemma = index.getLemma();

            // Уменьшаем частоту леммы на 1
            lemma.setFrequency(lemma.getFrequency() - 1);

            if (lemma.getFrequency() == 0) {
                // Если frequency стало 0, удаляем лемму
                lemmaRepository.delete(lemma);
            } else {
                // Иначе обновляем лемму
                lemmaRepository.save(lemma);
            }

            // Удаляем запись из таблицы index
            indexRepository.delete(index);
        }

        // Удаляем страницу из таблицы page
        pageRepository.delete(page);

        logger.info("Информация о странице удалена: {}", page.getPath());
    }

    @Transactional
    public void deleteSiteData(Site site) {
        // Удаление индексов, связанных с данным сайтом
        indexRepository.deleteByPage_Site(site.getId());

        // Удаление лемм, связанных с данным сайтом
        lemmaRepository.deleteBySite(site);

        // Удаление страниц, связанных с данным сайтом
        pageRepository.deleteBySite(site);

        logger.info("Все данные сайта {} удалены", site.getUrl());
    }
    @Transactional
    public void deletePageInfoIfExists(Site site, String url) {
        String path = url.replace(site.getUrl(), "");
        if (path.isEmpty()) path = "/";

        Optional<Page> existingPage = pageRepository.findBySiteAndPath(site.getId(), path);
        existingPage.ifPresent(this::deletePageInfo);
    }
}