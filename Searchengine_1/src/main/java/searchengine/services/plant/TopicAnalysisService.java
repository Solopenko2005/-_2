package searchengine.services.plant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import searchengine.model.Topic;
import searchengine.model.TopicGroup;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.util.*;

@Service
public class TopicAnalysisService {

    @PersistenceContext
    private EntityManager entityManager;

    // Ключевые слова для семеноводства и селекции
    private static final Map<String, List<String>> CATEGORY_KEYWORDS = new HashMap<>();

    // Технологические маркеры
    private static final List<String> TECH_MARKERS = Arrays.asList(
            "технология", "метод", "система", "подход", "методика",
            "инструмент", "платформа", "модель", "алгоритм", "ген",
            "редактирование", "маркер", "секвенирование", "геномный",
            "молекулярный", "селекция", "гибрид", "трансформация",
            "консервация", "обработка", "анализ", "влияние", "внедрение", "перспективы"
    );

    static {
        CATEGORY_KEYWORDS.put("plant", Arrays.asList(
                "семеноводство", "селекция растений", "производство семян",
                "качество семян", "всхожесть семян", "гибридные семена", "обработка семян",
                "молекулярная селекция", "генетическое улучшение", "технология семян",
                "генетика растений", "хранение семян", "сертификация семян", "традиционная селекция"
        ));
    }

    /**
     * DTO для хранения результатов анализа темы
     */
    public static class TopicAnalysisResult {
        private Topic topic;
        private int seedKeywordsFrequency;
        private int techKeywordsFrequency;
        private int totalFrequency;
        private Map<String, Integer> contentKeywordMatches;

        public TopicAnalysisResult(Topic topic) {
            this.topic = topic;
            this.seedKeywordsFrequency = 0;
            this.techKeywordsFrequency = 0;
            this.totalFrequency = 0;
            this.contentKeywordMatches = new LinkedHashMap<>();
        }

        public Topic getTopic() { return topic; }
        public int getSeedKeywordsFrequency() { return seedKeywordsFrequency; }
        public void setSeedKeywordsFrequency(int seedKeywordsFrequency) { this.seedKeywordsFrequency = seedKeywordsFrequency; }
        public int getTechKeywordsFrequency() { return techKeywordsFrequency; }
        public void setTechKeywordsFrequency(int techKeywordsFrequency) { this.techKeywordsFrequency = techKeywordsFrequency; }
        public int getTotalFrequency() { return totalFrequency; }
        public void setTotalFrequency(int totalFrequency) { this.totalFrequency = totalFrequency; }
        public Map<String, Integer> getContentKeywordMatches() { return contentKeywordMatches; }
        public void setContentKeywordMatches(Map<String, Integer> contentKeywordMatches) { this.contentKeywordMatches = contentKeywordMatches; }
    }

    /**
     * Основной метод для анализа тем с topic_group_id != null (т.е. у которых есть группа)
     * Теперь фильтруем только те темы, где обе частоты >= 1
     */
    public List<TopicAnalysisResult> analyzeTopicsWithGroup() {
        // Получаем все темы, у которых topicGroup не равен null
        List<Topic> topics = getTopicsWithGroup();

        List<TopicAnalysisResult> results = new ArrayList<>();

        for (Topic topic : topics) {
            TopicAnalysisResult result = analyzeSingleTopic(topic);
            // Фильтруем: обе частоты должны быть >= 1
            if (result.getSeedKeywordsFrequency() >= 1 && result.getTechKeywordsFrequency() >= 1) {
                results.add(result);
            }
        }

        // Сортируем по общей частоте (по убыванию)
        results.sort((r1, r2) -> Integer.compare(r2.getTotalFrequency(), r1.getTotalFrequency()));

        return results;
    }

    /**
     * Получение тем, у которых есть группа (topicGroup не null)
     */
    private List<Topic> getTopicsWithGroup() {
        String hql = "SELECT t FROM Topic t WHERE t.topicGroup IS NOT NULL";
        TypedQuery<Topic> query = entityManager.createQuery(hql, Topic.class);
        return query.getResultList();
    }

    /**
     * Анализ отдельной темы
     * Поиск ключевых слов происходит в заголовке и контенте для первого файла
     */
    private TopicAnalysisResult analyzeSingleTopic(Topic topic) {
        TopicAnalysisResult result = new TopicAnalysisResult(topic);

        // Анализируем title (для подсчета частоты в первом файле)
        analyzeTextForKeywords(topic.getTitle(), result);

        // Анализируем content (для подсчета частоты в первом файле)
        analyzeTextForKeywords(topic.getContent(), result);

        // Для второго файла: анализируем контент отдельно
        Map<String, Integer> contentMatches = analyzeContentOnly(topic.getContent());
        result.setContentKeywordMatches(contentMatches);

        // Рассчитываем общую частоту
        int totalFrequency = result.getSeedKeywordsFrequency() + result.getTechKeywordsFrequency();
        result.setTotalFrequency(totalFrequency);

        return result;
    }

    /**
     * Анализ текста на наличие ключевых слов и технологических маркеров
     */
    private void analyzeTextForKeywords(String text, TopicAnalysisResult result) {
        if (text == null || text.isEmpty()) return;

        String lowerText = text.toLowerCase();

        // Поиск ключевых слов категории
        for (Map.Entry<String, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                int count = countOccurrences(lowerText, keyword.toLowerCase());
                if (count > 0) {
                    result.setSeedKeywordsFrequency(result.getSeedKeywordsFrequency() + count);
                }
            }
        }

        // Поиск технологических маркеров
        for (String marker : TECH_MARKERS) {
            int count = countOccurrences(lowerText, marker.toLowerCase());
            if (count > 0) {
                result.setTechKeywordsFrequency(result.getTechKeywordsFrequency() + count);
            }
        }
    }

    /**
     * Анализ ТОЛЬКО контента для получения списка найденных слов с частотами
     * (для второго файла - экспорт контента)
     */
    private Map<String, Integer> analyzeContentOnly(String content) {
        Map<String, Integer> matches = new LinkedHashMap<>();

        if (content == null || content.isEmpty()) return matches;

        String lowerContent = content.toLowerCase();

        // Добавляем ключевые слова категории
        for (Map.Entry<String, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                int count = countOccurrences(lowerContent, keyword.toLowerCase());
                if (count > 0) {
                    matches.put(keyword, count);
                }
            }
        }

        // Добавляем технологические маркеры
        for (String marker : TECH_MARKERS) {
            int count = countOccurrences(lowerContent, marker.toLowerCase());
            if (count > 0) {
                matches.put(marker, count);
            }
        }

        return matches;
    }

    /**
     * Подсчет количества вхождений подстроки в строку
     */
    private int countOccurrences(String text, String searchString) {
        if (text == null || searchString == null || text.isEmpty()) return 0;

        int count = 0;
        int index = 0;
        while ((index = text.indexOf(searchString, index)) != -1) {
            count++;
            index += searchString.length();
        }
        return count;
    }

    /**
     * Экспорт результатов в текстовый формат (темы с найденными ключевыми словами)
     * Только темы, где есть И семеноводческие термины, И технологические маркеры
     */
    public String exportTopicsWithKeywords(int limit) {
        List<TopicAnalysisResult> results = analyzeTopicsWithGroup();

        StringBuilder sb = new StringBuilder();
        sb.append("ТОП-").append(Math.min(limit, results.size())).append(" ТЕМ С НАЙДЕННЫМИ КЛЮЧЕВЫМИ СЛОВАМИ\n");
        sb.append("(отобраны темы, содержащие и семеноводческие термины, и технологические маркеры)\n");
        sb.append("================================================================\n\n");

        int counter = 1;
        for (TopicAnalysisResult result : results) {
            if (counter > limit) break;

            Topic topic = result.getTopic();

            sb.append(counter).append(". ").append(topic.getTitle()).append("\n");
            sb.append("   ID темы: ").append(topic.getId()).append("\n");
            if (topic.getTopicGroup() != null) {
                sb.append("   ID группы: ").append(topic.getTopicGroup().getId()).append("\n");
                sb.append("   Группа: ").append(topic.getTopicGroup().getTitle()).append("\n");
            }
            sb.append("   Частота ключевых слов (семеноводство/селекция): ").append(result.getSeedKeywordsFrequency()).append("\n");
            sb.append("   Частота технологических маркеров: ").append(result.getTechKeywordsFrequency()).append("\n");
            sb.append("   Общая частота: ").append(result.getTotalFrequency()).append("\n");

            if (!result.getContentKeywordMatches().isEmpty()) {
                sb.append("   Найденные ключевые слова:\n");
                for (Map.Entry<String, Integer> entry : result.getContentKeywordMatches().entrySet()) {
                    sb.append("     - ").append(entry.getKey()).append(" (").append(entry.getValue()).append(" раз(а))\n");
                }
            }
            sb.append("\n");
            counter++;
        }

        if (results.isEmpty()) {
            sb.append("Темы, содержащие одновременно семеноводческие термины и технологические маркеры, не найдены.\n");
        }

        return sb.toString();
    }

    /**
     * Экспорт контента с частотами слов (второй файл)
     * Выводится только контент и общая частота для каждой темы
     */
    public String exportContentWithFrequencies(int limit) {
        List<TopicAnalysisResult> results = analyzeTopicsWithGroup();

        StringBuilder sb = new StringBuilder();
        sb.append("КОНТЕНТ ТЕМ С ЧАСТОТАМИ КЛЮЧЕВЫХ СЛОВ\n");
        sb.append("(отобраны темы, содержащие и семеноводческие термины, и технологические маркеры)\n");
        sb.append("===================================\n\n");

        int counter = 1;
        for (TopicAnalysisResult result : results) {
            if (counter > limit) break;

            Topic topic = result.getTopic();

            sb.append("=== ТЕМА #").append(counter).append(" ===\n");
            sb.append("Заголовок: ").append(topic.getTitle()).append("\n");
            sb.append("ID темы: ").append(topic.getId()).append("\n");
            if (topic.getTopicGroup() != null) {
                sb.append("ID группы: ").append(topic.getTopicGroup().getId()).append("\n");
            }
            sb.append("Общая частота ключевых слов: ").append(result.getTotalFrequency()).append("\n\n");

            sb.append("КОНТЕНТ:\n");
            String content = topic.getContent();
            if (content != null && !content.isEmpty()) {
                sb.append(content);
            } else {
                sb.append("(контент отсутствует)");
            }
            sb.append("\n\n").append("=".repeat(80)).append("\n\n");

            counter++;
        }

        if (results.isEmpty()) {
            sb.append("Темы, содержащие одновременно семеноводческие термины и технологические маркеры, не найдены.\n");
        }

        return sb.toString();
    }
}