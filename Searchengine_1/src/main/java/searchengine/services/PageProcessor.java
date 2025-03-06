package searchengine.services;

import lombok.SneakyThrows;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
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
    private RussianLuceneMorphology luceneMorphology;

    private final AtomicBoolean isIndexingStopped = new AtomicBoolean(false);
    private final int delayBetweenRequests = 500; // Задержка между запросами в миллисекундах

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
        if (depth > 10) {
            logger.warn("Достигнута максимальная глубина рекурсии для URL: {}", url);
            return;
        }
        if (pageRepository.existsBySiteAndPath(site, url)) {
            logger.info("Страница уже проиндексирована: {}", url);
            return; // Страница уже проиндексирована
        }

        Connection connection = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .referrer("https://www.google.com")
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

        // Лемматизация текста
        Map<String, Integer> lemmas = searchingLemmasAndTheirCount(text);

        // Сохранение лемм и индексов
        for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
            String lemmaText = entry.getKey();
            int rank = entry.getValue();

            // Поиск или создание леммы
            Lemma lemma = findOrCreateLemma(site, lemmaText);

            // Увеличиваем частоту леммы
            lemma.setFrequency(lemma.getFrequency() + 1);
            lemmaRepository.save(lemma);

            // Создание и сохранение индекса
            SearchIndex index = new SearchIndex();
            index.setPage(page);
            index.setLemma(lemma);
            index.setRanking((float) rank);
            indexRepository.save(index);

            logger.info("Лемма '{}' добавлена для страницы {}", lemmaText, page.getPath());
        }
    }

    /**
     * Поиск или создание леммы
     *
     * @param site      Сайт
     * @param lemmaText Текст леммы
     * @return Лемма
     */
    private Lemma findOrCreateLemma(Site site, String lemmaText) {
        Optional<Lemma> lemmaOptional = lemmaRepository.findByLemmaAndSite(lemmaText, site);
        if (lemmaOptional.isPresent()) {
            return lemmaOptional.get(); // Возвращаем найденную лемму
        } else {
            Lemma newLemma = new Lemma();
            newLemma.setSite(site);
            newLemma.setLemma(lemmaText);
            newLemma.setFrequency(0);
            return lemmaRepository.save(newLemma);
        }
    }

    /**
     * Метод поиска лемм и их количества в тексте
     */
    @SneakyThrows
    public Map<String, Integer> searchingLemmasAndTheirCount(String text) {
        Map<String, Integer> lemmasMap = new HashMap<>();
        String[] arrayWords = convertingTextToArray(text);

        for (String word : arrayWords) {
            if (word.isBlank()) continue;

            List<String> wordBaseForms = luceneMorphology.getMorphInfo(word);
            if (checkComplianceWordToParticlesNames(wordBaseForms)) continue;

            List<String> wordNormalFormList = luceneMorphology.getNormalForms(word);
            if (!wordNormalFormList.isEmpty()) {
                String wordInNormalForm = wordNormalFormList.get(0); // Берем первую нормальную форму
                lemmasMap.put(wordInNormalForm, lemmasMap.getOrDefault(wordInNormalForm, 0) + 1);
            }
        }
        return lemmasMap;
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
}