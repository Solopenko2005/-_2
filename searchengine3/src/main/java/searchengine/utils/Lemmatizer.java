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

    // Конструктор для инициализации зависимостей
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

    /**
     * Возвращает карту лемм из текста с указанием частоты встречаемости.
     *
     * @param text исходный текст
     * @return Map, где ключ — лемма, значение — её частота
     */
    public static Map<String, Integer> getLemmas(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyMap();
        }
        // Приводим текст к нижнему регистру и оставляем только русские буквы и пробелы
        text = text.toLowerCase().replaceAll("[^а-яё ]", "");

        Map<String, Integer> lemmas = new HashMap<>();

        try {
            // Разбиваем текст на слова
            for (String word : text.split("\\s+")) {
                if (word.isEmpty()) continue; // Пропускаем пустые строки

                // Получаем нормальные формы слова
                List<String> wordNormalForms = luceneMorph.getNormalForms(word);
                if (!wordNormalForms.isEmpty()) {
                    String lemma = wordNormalForms.get(0);
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

    /**
     * Извлекает множество лемм из текста.
     *
     * @param text исходный текст
     * @return множество уникальных лемм
     */
    public static Set<String> extractLemmas(String text) {
        return getLemmas(text).keySet();
    }

    /**
     * Получает суммарную частоту леммы из базы данных.
     *
     * @param lemma текст леммы
     * @return частота леммы
     */
    public int getLemmaFrequency(String lemma) {
        // Открытие сессии Hibernate
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "SELECT SUM(frequency) FROM Lemma WHERE lemma = :lemma";
            Query<Long> query = session.createQuery(hql, Long.class);
            query.setParameter("lemma", lemma);
            Long result = query.uniqueResult();
            return result != null ? result.intValue() : 0;
        }
    }

    /**
     * Проверяет, является ли слово стоп-словом.
     *
     * @param word слово для проверки
     * @return true, если слово является стоп-словом, иначе false
     */
    private static boolean isStopWord(String word) {
        // Список стоп-слов (можно расширить или загрузить из внешнего источника)
        List<String> stopWords = Arrays.asList("и", "в", "на", "с", "по", "за", "из", "у", "для");
        return stopWords.contains(word);
    }

    /**
     * Удаляет HTML-теги из строки.
     *
     * @param html исходная HTML-строка
     * @return текст без HTML-тегов
     */
    public static String removeHtmlTags(String html) {
        return Jsoup.parse(html).text();
    }
}
