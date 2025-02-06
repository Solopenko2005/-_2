package searchengine.services;

import lombok.SneakyThrows;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Connection;
import org.springframework.stereotype.Service;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import searchengine.model.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class PageProcessor {

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
    private Object pagePath;

    /**
     * Метод для обработки страницы и сохранения данных в БД
     */
    public void parsePageAndSaveEntitiesToDB(String pageUrl, int siteId) throws IOException {
        // Получение сайта из БД
        Site site = siteRepository.findById(Long.valueOf(siteId)).orElseThrow(() -> new RuntimeException("Сайт не найден"));


        // Получение HTML-кода страницы
        Connection.Response response = Jsoup.connect(pageUrl)
                .ignoreHttpErrors(true)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .referrer("https://www.google.com")
                .execute();

        int statusCode = response.statusCode();
        if (statusCode >= 400) {
            throw new RuntimeException("Ошибка HTTP: " + statusCode);
        }

        String content = response.body();
        String textContent = Jsoup.parse(content).text();
        // Проверка или создание записи в таблице Page
        Page page = pageRepository.findByPath((String) pagePath).orElseGet(() -> {
            // Создание сущности страницы
            Page newPage = new Page();
            newPage.setUrl((String) pagePath);
            newPage.setSite(site);
            newPage.setContent(content);
            newPage.setPath(((String) pagePath).replace(site.getUrl(), ""));
            return pageRepository.save(newPage);
        });

        // Получение лемм и их количества
        Map<String, Integer> pageLemmasMap = searchingLemmasAndTheirCount(textContent);

        // Сохранение данных в таблицы lemma и index
        for (Map.Entry<String, Integer> pair : pageLemmasMap.entrySet()) {
            String lemmaText = pair.getKey();
            int lemmaCount = pair.getValue();

            Lemma lemmaEntity = lemmaRepository.findByLemmaAndSite(lemmaText, site)
                    .orElseGet(() -> {
                        Lemma newLemma = new Lemma();
                        newLemma.setLemma(lemmaText);
                        newLemma.setSite(site);
                        newLemma.setFrequency(0);
                        return lemmaRepository.save(newLemma);
                    });

            // Увеличиваем частоту леммы
            lemmaEntity.setFrequency(lemmaEntity.getFrequency() + 1);
            lemmaRepository.save(lemmaEntity);
            // Добавляем запись в таблицу index для связи леммы с страницей
            SearchIndex index = new SearchIndex();
            index.setPage(page);
            index.setLemma(lemmaEntity);
            index.setRanking(lemmaCount);  // Рейтинг леммы на странице
            indexRepository.save(index);
        }
    }


    /**
     * Вспомогательный метод для получения пути страницы
     */
    private String getPagePath(String pageUrl) {
        try {
            URL url = new URL(pageUrl);
            return url.getPath();
        } catch (MalformedURLException e) {
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
