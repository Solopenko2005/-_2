package searchengine.services;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TopicLemmatizerService {

    private final LemmaService lemmaService;
    private final Set<String> stopWords = new HashSet<>(Arrays.asList(
            "в", "на", "о", "об", "по", "с", "из", "от", "до", "для", "за", "к", "у", "при", "под",
            "the", "of", "in", "on", "at", "to", "for", "with", "by", "from", "and", "or", "but"
    ));

    public TopicLemmatizerService(LemmaService lemmaService) {
        this.lemmaService = lemmaService;
    }

    /**
     * Извлечь ключевые леммы из темы
     */
    public Set<String> extractKeyLemmas(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new HashSet<>();
        }

        // Нормализация текста
        String normalized = normalizeText(text);

        // Разбиваем на слова
        String[] words = normalized.split("\\s+");

        // Фильтруем стоп-слова и короткие слова
        Set<String> keyLemmas = new HashSet<>();

        for (String word : words) {
            if (word.length() > 2 && !stopWords.contains(word.toLowerCase())) {
                // Получаем лемму для слова
                String lemma = lemmaService.getLemma(word);
                if (lemma != null && !lemma.isEmpty()) {
                    keyLemmas.add(lemma.toLowerCase());
                }
            }
        }

        return keyLemmas;
    }

    /**
     * Вычислить схожесть тем на основе лемм
     */
    public double calculateSimilarity(Set<String> lemmas1, Set<String> lemmas2) {
        if (lemmas1.isEmpty() || lemmas2.isEmpty()) {
            return 0.0;
        }

        // Находим пересечение лемм
        Set<String> intersection = new HashSet<>(lemmas1);
        intersection.retainAll(lemmas2);

        // Находим объединение лемм
        Set<String> union = new HashSet<>(lemmas1);
        union.addAll(lemmas2);

        // Коэффициент Жаккара
        return (double) intersection.size() / union.size();
    }

    /**
     * Нормализация текста
     */
    private String normalizeText(String text) {
        return text.toLowerCase()
                .replaceAll("[^а-яёa-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Извлечь тематические ключевые слова (самые важные леммы)
     */
    public Set<String> extractTopLemmas(String text, int topN) {
        Set<String> allLemmas = extractKeyLemmas(text);

        // Можно добавить веса лемм на основе TF-IDF или частоты
        return allLemmas.stream()
                .limit(topN)
                .collect(Collectors.toSet());
    }
}