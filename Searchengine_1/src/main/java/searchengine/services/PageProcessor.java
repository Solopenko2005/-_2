package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
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
import java.util.concurrent.atomic.AtomicBoolean;

@RequiredArgsConstructor
@Service
public class PageProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PageProcessor.class);

    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final SiteRepository siteRepository;
    private final Lemmatizer lemmatizer;
    private final IndexingState indexingState;

    private final AtomicBoolean isIndexingStopped = new AtomicBoolean(false);
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

            Optional<Page> existingPage = pageRepository.findBySiteAndPath(site.getId(), path);
            existingPage.ifPresent(this::deletePageInfo);

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
            throw e;
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
        Map<String, Integer> lemmas = lemmatizer.extractLemmasWithRank(text);

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
    @Transactional
    private Lemma findOrCreateLemma(Site site, String lemmaText) {
        return lemmaRepository.findByLemmaAndSiteId(lemmaText, site.getId())
                .orElseGet(() -> {
                    Lemma newLemma = new Lemma();
                    newLemma.setSite(site);
                    newLemma.setLemma(lemmaText);
                    newLemma.setFrequency(1);
                    return lemmaRepository.save(newLemma);
                });
    }

    /**
     * Удаляет информацию о странице из таблиц page, lemma и index.
     *
     * @param page Страница
     */
    @Transactional
    public void deletePageInfo(Page page) {

        List<SearchIndex> indexes = indexRepository.findByPage(page);
        for (SearchIndex index : indexes) {
            Lemma lemma = index.getLemma();
            lemma.setFrequency(lemma.getFrequency() - 1);
            if (lemma.getFrequency() == 0) {
                lemmaRepository.delete(lemma);
            } else {
                lemmaRepository.save(lemma);
            }

            indexRepository.delete(index);
        }

        pageRepository.delete(page);

        logger.info("Информация о странице удалена: {}", page.getPath());
    }

    @Transactional
    public void deletePageInfoIfExists(Site site, String url) {
        String path = url.replace(site.getUrl(), "");
        if (path.isEmpty()) path = "/";

        Optional<Page> existingPage = pageRepository.findBySiteAndPath(site.getId(), path);
        existingPage.ifPresent(this::deletePageInfo);
    }
}