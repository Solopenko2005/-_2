package searchengine.services;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import searchengine.config.IndexingState;
import searchengine.repository.LemmaRepository;
import searchengine.repository.SiteRepository;
import searchengine.utils.HibernateUtil;

import java.io.IOException;
import java.util.*;

@Service
public class Lemmatizer {

    private static final LuceneMorphology luceneMorph;
    private final IndexingState indexingState; // Убран static
    private final LemmaRepository lemmaRepository;

    static {
        try {
            luceneMorph = new RussianLuceneMorphology();
        } catch (IOException e) {
            throw new RuntimeException("Ошибка инициализации морфологии", e);
        }
    }

    @Autowired
    public Lemmatizer(IndexingState indexingState, LemmaRepository lemmaRepository) {
        this.indexingState = indexingState;
        this.lemmaRepository = lemmaRepository;
    }

    // Убран static, добавлена проверка состояния
    public Map<String, Integer> getLemmas(String text) {
        if (indexingState.isStopRequested()) return Collections.emptyMap();

        if (text == null || text.isEmpty()) {
            return Collections.emptyMap();
        }
        text = text.toLowerCase().replaceAll("[^а-яё ]", "");
        Map<String, Integer> lemmas = new HashMap<>();

        try {
            for (String word : text.split("\\s+")) {
                if (indexingState.isStopRequested()) break;
                if (word.isEmpty()) continue;
                List<String> normalForms = luceneMorph.getNormalForms(word);
                if (!normalForms.isEmpty()) {
                    String lemma = normalForms.get(0);
                    if (!isStopWord(lemma)) {
                        lemmas.put(lemma, lemmas.getOrDefault(lemma, 0) + 1);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Ошибка при обработке текста: " + e.getMessage());
        }

        return lemmas;
    }

    public Map<String, Integer> extractLemmasWithRank(String text) {
        if (indexingState.isStopRequested()) return Collections.emptyMap();
        return getLemmas(text);
    }

    private boolean isStopWord(String word) {
        List<String> stopWords = Arrays.asList("и", "в", "на", "с", "по", "за", "из", "у", "для");
        return stopWords.contains(word);
    }

}