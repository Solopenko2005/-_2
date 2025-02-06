package searchengine.utils;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.jsoup.Jsoup;
import org.springframework.jdbc.core.JdbcTemplate;
import searchengine.repository.LemmaRepository;
import searchengine.repository.SiteRepository;

import java.io.IOException;
import java.util.*;

public class Lemmatizer {

    private static final LuceneMorphology luceneMorph;
    private final JdbcTemplate jdbcTemplate;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    // Конструктор для инициализации JdbcTemplate
    public Lemmatizer(JdbcTemplate jdbcTemplate, SiteRepository siteRepository, LemmaRepository lemmaRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.siteRepository = siteRepository;
        this.lemmaRepository = lemmaRepository;
    }

    // Статический блок инициализации LuceneMorphology
    static {
        try {
            luceneMorph = new RussianLuceneMorphology();
        } catch (IOException e) {
            throw new RuntimeException("Ошибка инициализации LuceneMorphology", e);
        }
    }

    // Метод для получения лемм и их частоты
    public static Map<String, Integer> getLemmas(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyMap();
        }

        // Оставляем только русские буквы и пробелы
        text = text.toLowerCase().replaceAll("[^а-яё ]", "");

        Map<String, Integer> lemmas = new HashMap<>();

        try {
            // Разделяем текст на слова и получаем леммы
            for (String word : text.split("\\s+")) {
                if (word.isEmpty()) continue; // Пропускаем пустые строки

                // Получаем нормальную форму слова
                List<String> wordBaseForms = luceneMorph.getNormalForms(word);
                if (!wordBaseForms.isEmpty()) {
                    String lemma = wordBaseForms.get(0);

                    // Обновляем или добавляем лемму в карту с частотами
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

    // Метод для извлечения множества лемм из текста
    public static Set<String> extractLemmas(String text) {
        return getLemmas(text).keySet();
    }

    // Метод для получения частоты леммы из базы данных
    public int getLemmaFrequency(String lemma) {
        // Открытие сессии Hibernate
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            // Создание HQL запроса для получения частоты леммы
            String hql = "SELECT SUM(frequency) FROM Lemma WHERE lemma = :lemma";
            Query<Long> query = session.createQuery(hql, Long.class);
            query.setParameter("lemma", lemma);

            Long result = query.uniqueResult();
            return result != null ? result.intValue() : 0;
        }
    }

    // Метод для проверки, является ли слово стоп-словом
    private static boolean isStopWord(String word) {
        // Можно загрузить стоп-слова из внешнего источника, базы данных и т. д.
        List<String> stopWords = Arrays.asList("и", "в", "на", "с", "по", "за", "из", "у", "для");
        return stopWords.contains(word);
    }

    // Метод для удаления HTML тегов
    public static String removeHtmlTags(String html) {
        return Jsoup.parse(html).text();
    }
}
