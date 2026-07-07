package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Site;
import searchengine.repository.LemmaRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

@Service
@RequiredArgsConstructor
public class LemmaService {
    public static final Logger logger = LoggerFactory.getLogger(LemmaService.class);
    private final LemmaRepository lemmaRepository;

    @Transactional
    public void saveOrUpdateLemma(String lemmaText, Site site) {
        if (site == null) {
            logger.error("Ошибка: site == null при сохранении леммы '{}'", lemmaText);
            throw new IllegalArgumentException("Ошибка: site не может быть null при сохранении леммы");
        }

        try {
            lemmaRepository.upsertLemma(lemmaText, site.getId());
            logger.info("Лемма '{}' добавлена/обновлена для сайта '{}'", lemmaText, site.getUrl());
        } catch (Exception e) {
            logger.error("Ошибка при сохранении леммы '{}': {}", lemmaText, e.getMessage());
            throw new RuntimeException("Ошибка при сохранении леммы: " + e.getMessage());
        }
    }

    /**
     * Получить лемму для слова (упрощенная реализация)
     */
    public String getLemma(String word) {
        if (word == null || word.trim().isEmpty()) {
            return "";
        }

        String normalized = word.toLowerCase().trim();

        // Базовые правила для русского языка
        return normalizeRussianWord(normalized);
    }

    /**
     * Извлечь леммы из текста с частотами
     */
    public Map<String, Integer> extractLemmasWithRank(String text) {
        Map<String, Integer> lemmas = new HashMap<>();

        if (text == null || text.trim().isEmpty()) {
            return lemmas;
        }

        // Удаляем спецсимволы и разбиваем на слова
        String[] words = text.toLowerCase()
                .replaceAll("[^а-яёa-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .split("\\s+");

        // Стоп-слова
        Set<String> stopWords = new HashSet<>();
        stopWords.add("и");
        stopWords.add("в");
        stopWords.add("на");
        stopWords.add("с");
        stopWords.add("по");
        stopWords.add("о");
        stopWords.add("для");
        stopWords.add("из");
        stopWords.add("от");
        stopWords.add("до");

        for (String word : words) {
            // Пропускаем стоп-слова и короткие слова
            if (word.length() > 2 && !stopWords.contains(word)) {
                String lemma = getLemma(word);
                if (!lemma.isEmpty()) {
                    lemmas.put(lemma, lemmas.getOrDefault(lemma, 0) + 1);
                }
            }
        }

        return lemmas;
    }

    /**
     * Нормализация русского слова (упрощенная лемматизация)
     */
    private String normalizeRussianWord(String word) {
        if (word.length() < 3) {
            return word;
        }

        // Список окончаний для удаления
        String[] endings = {
                "ый", "ий", "ой", "ая", "яя", "ое", "ее", "ые", "ие", "ом", "ем", "ой", "ей",
                "ам", "ям", "ов", "ев", "ых", "их", "ую", "юю", "ми", "ти", "ть", "ся", "сь"
        };

        // Проверяем окончания
        for (String ending : endings) {
            if (word.endsWith(ending) && word.length() > ending.length() + 1) {
                return word.substring(0, word.length() - ending.length());
            }
        }

        // Проверяем одиночные окончания
        char lastChar = word.charAt(word.length() - 1);
        if (lastChar == 'ы' || lastChar == 'и' || lastChar == 'а' || lastChar == 'я' ||
                lastChar == 'о' || lastChar == 'е' || lastChar == 'у' || lastChar == 'ю') {
            return word.substring(0, word.length() - 1);
        }

        return word;
    }

    /**
     * Извлечь ключевые леммы из текста
     */
    public Set<String> extractKeyLemmas(String text) {
        Map<String, Integer> lemmasWithRank = extractLemmasWithRank(text);

        // Возвращаем только леммы (без частот)
        return lemmasWithRank.keySet();
    }
}