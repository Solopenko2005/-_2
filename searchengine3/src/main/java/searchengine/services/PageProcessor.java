package searchengine.services;

import lombok.SneakyThrows;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.model.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

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

    /**
     * Метод для обработки страницы и сохранения данных в БД
     */
    public void parsePageAndSaveEntitiesToDB(String pageUrl, int siteId) throws IOException {
        // Получение сайта из БД
        Site site = siteRepository.findById(siteId)
                .orElseThrow(() -> new RuntimeException("Сайт не найден: ID " + siteId));

        // Получение пути страницы (относительный путь)
        String pagePath = getPagePath(pageUrl);
        if (pagePath == null || pagePath.isEmpty()) {
            logger.error("Ошибка: pagePath для URL {} не может быть null или пустым", pageUrl);
            throw new RuntimeException("Некорректный путь страницы: " + pageUrl);
        }

        // Получение HTML-кода страницы
        Connection.Response response = Jsoup.connect(pageUrl)
                .ignoreHttpErrors(true)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .referrer("https://www.google.com")
                .execute();

        int statusCode = response.statusCode();
        if (statusCode >= 400) {
            logger.error("Ошибка HTTP {} при попытке загрузить {}", statusCode, pageUrl);
            throw new RuntimeException("Ошибка HTTP: " + statusCode);
        }

        String content = response.body();
        String textContent = Jsoup.parse(content).text();

        // Проверка или создание записи в таблице Page
        Page page = pageRepository.findByPathAndSite(pagePath, site).orElseGet(() -> {
            Page newPage = new Page();
            newPage.setPath(pagePath);
            newPage.setUrl(pageUrl);
            newPage.setSite(site);
            newPage.setContent(content);
            newPage.setCode(statusCode);  // Убеждаемся, что HTTP-код установлен
            return pageRepository.save(newPage);
        });


        // Получение лемм и их количества
        Map<String, Integer> pageLemmasMap = searchingLemmasAndTheirCount(textContent);

        // Сохранение данных в таблицы lemma и index
        for (Map.Entry<String, Integer> pair : pageLemmasMap.entrySet()) {
            String lemmaText = pair.getKey();
            int lemmaCount = pair.getValue();

            lemmaRepository.upsertLemma(lemmaText, site.getId());

            Lemma lemmaEntity = lemmaRepository.findByLemmaAndSite(lemmaText, site)
                    .orElseThrow(() -> new RuntimeException("Лемма не найдена после upsert: " + lemmaText));

            // Добавление связи леммы со страницей
            SearchIndex index = new SearchIndex();
            index.setPage(page);
            index.setLemma(lemmaEntity);
            index.setRanking(lemmaCount);
            indexRepository.save(index);
        }
    }

    /**
     * Вспомогательный метод для получения пути страницы
     */
    private String getPagePath(String pageUrl) {
        try {
            URL url = new URL(pageUrl);
            return url.getPath().isEmpty() ? "/" : url.getPath();
        } catch (MalformedURLException e) {
            logger.error("Некорректный URL: {}", pageUrl, e);
            throw new RuntimeException("Некорректный URL: " + pageUrl);
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
            String wordInNormalForm = wordNormalFormList.get(0);

            lemmasMap.put(wordInNormalForm, lemmasMap.getOrDefault(wordInNormalForm, 0) + 1);
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
}
