package searchengine.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.model.TopicGroup;
import searchengine.repository.TopicRepository;
import searchengine.services.AdvancedTopicGroupingService;
import searchengine.services.EnhancedTopicFilterService;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/topics")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TopTopicsController {

    private final AdvancedTopicGroupingService groupingService;
    private final EnhancedTopicFilterService enhancedFilterService;
    private final TopicRepository topicRepository;

    /**
     * Топ-50 тем по убыванию частоты (очищенные)
     * GET /api/topics/top-50?clean=true
     */
    @GetMapping("/top-50")
    public ResponseEntity<Map<String, Object>> getTop50Topics(
            @RequestParam(defaultValue = "true") boolean clean,
            @RequestParam(defaultValue = "frequency") String sortBy) {

        Map<String, Object> response = new HashMap<>();

        try {
            List<TopicGroup> allGroups = groupingService.getAllGroupsSorted();

            // Фильтруем и сортируем
            List<TopicGroup> filteredGroups = allGroups.stream()
                    .filter(group -> {
                        if (clean) {
                            return enhancedFilterService.isRelevantAndCleanTopic(group);
                        } else {
                            return group.getFrequency() >= 2;
                        }
                    })
                    .sorted(getTopicComparator(sortBy))
                    .limit(50)
                    .collect(Collectors.toList());

            // Формируем ответ
            List<Map<String, Object>> topicsList = new ArrayList<>();
            int rank = 1;

            for (TopicGroup group : filteredGroups) {
                Map<String, Object> topicInfo = new HashMap<>();
                topicInfo.put("rank", rank++);

                // Очищаем заголовок
                String cleanTitle = enhancedFilterService.cleanTopicTitle(group.getTitle());
                if (cleanTitle.length() > 80) {
                    cleanTitle = cleanTitle.substring(0, 80) + "...";
                }
                topicInfo.put("theme", cleanTitle);

                // Основная статистика
                topicInfo.put("frequency", group.getFrequency());
                topicInfo.put("mentions", group.getSiteCount()); // количество сайтов
                topicInfo.put("groupId", group.getId());

                // Получаем сайты
                List<String> sites = topicRepository.findByTopicGroupId(group.getId())
                        .stream()
                        .map(topic -> topic.getSite().getName())
                        .distinct()
                        .collect(Collectors.toList());
                topicInfo.put("sites", sites);

                // Дополнительная информация
                topicInfo.put("originalTitle", group.getTitle());

                topicsList.add(topicInfo);
            }

            // Общая статистика
            int totalFiltered = filteredGroups.size();
            int totalOriginal = allGroups.size();

            Map<String, Object> statistics = new HashMap<>();
            statistics.put("totalTopics", totalFiltered);
            statistics.put("totalGroupsInSystem", totalOriginal);
            statistics.put("filteredOut", totalOriginal - totalFiltered);

            if (!filteredGroups.isEmpty()) {
                statistics.put("maxFrequency", filteredGroups.get(0).getFrequency());
                statistics.put("minFrequency", filteredGroups.get(filteredGroups.size() - 1).getFrequency());
                statistics.put("avgFrequency", filteredGroups.stream()
                        .mapToInt(TopicGroup::getFrequency)
                        .average()
                        .orElse(0));
            }

            response.put("result", true);
            response.put("data", topicsList);
            response.put("count", topicsList.size());
            response.put("statistics", statistics);
            response.put("filters", Map.of(
                    "clean", clean,
                    "sortBy", sortBy
            ));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("result", false);
            response.put("error", "Ошибка: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Компаратор для сортировки
     */
    private Comparator<TopicGroup> getTopicComparator(String sortBy) {
        Comparator<TopicGroup> comparator;

        switch (sortBy.toLowerCase()) {
            case "frequency":
                comparator = Comparator.comparingInt(TopicGroup::getFrequency);
                break;
            case "sitecount":
                comparator = Comparator.comparingInt(TopicGroup::getSiteCount);
                break;
            case "title":
                comparator = Comparator.comparing(TopicGroup::getTitle);
                break;
            default:
                comparator = Comparator.comparingInt(TopicGroup::getFrequency);
        }

        // Всегда по убыванию для топа
        return comparator.reversed();
    }

    /**
     * Поиск качественных тем по категориям
     * GET /api/topics/quality?category=plant&limit=20
     */
    @GetMapping("/quality")
    public ResponseEntity<Map<String, Object>> getQualityTopics(
            @RequestParam(defaultValue = "all") String category,
            @RequestParam(defaultValue = "20") int limit) {

        Map<String, Object> response = new HashMap<>();

        try {
            List<TopicGroup> allGroups = groupingService.getAllGroupsSorted();

            // Ключевые слова для категорий
            Map<String, List<String>> categoryKeywords = new HashMap<>();
            categoryKeywords.put("plant", Arrays.asList("семеноводство","селекция",
                    "селекция растений", "производство семян", "качество семян",
                    "всхожесть семян", "гибридные семена", "обработка семян",
                    "молекулярная селекция", "генетическое улучшение", "технология семян",
                    "генетика растений", "хранение семян", "сертификация семян", "традиционная селекция"));
            categoryKeywords.put("animal", Arrays.asList("животноводство", "скотоводство", "молочный",
                    "мясо", "корма", "ветеринария", "поголовье"));
            categoryKeywords.put("tech", Arrays.asList("техника", "трактор", "комбайн", "оборудование",
                    "цифровизация", "ии", "робот"));
            categoryKeywords.put("economy", Arrays.asList("экономика", "экспорт", "импорт", "рынок",
                    "цены", "инвестиции", "бюджет"));

            List<Map<String, ? extends Serializable>> qualityTopics = allGroups.stream()
                    .filter(enhancedFilterService::isRelevantAndCleanTopic)
                    .filter(group -> {
                        if ("all".equals(category)) {
                            return true;
                        }

                        String title = group.getTitle().toLowerCase();
                        List<String> keywords = categoryKeywords.getOrDefault(category, new ArrayList<>());

                        return keywords.stream().anyMatch(title::contains);
                    })
                    .sorted(Comparator.comparingInt(TopicGroup::getFrequency).reversed())
                    .limit(limit)
                    .map(group -> {
                        String cleanTitle = enhancedFilterService.cleanTopicTitle(group.getTitle());

                        return Map.of(
                                "theme", cleanTitle,
                                "frequency", group.getFrequency(),
                                "mentions", group.getSiteCount(),
                                "category", detectCategory(group.getTitle(), categoryKeywords),
                                "originalLength", group.getTitle().length(),
                                "cleanedLength", cleanTitle.length()
                        );
                    })
                    .collect(Collectors.toList());

            response.put("result", true);
            response.put("category", category);
            response.put("data", qualityTopics);
            response.put("count", qualityTopics.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("result", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Определение категории темы
     */
    private String detectCategory(String title, Map<String, List<String>> categoryKeywords) {
        String lowerTitle = title.toLowerCase();

        for (Map.Entry<String, List<String>> entry : categoryKeywords.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (lowerTitle.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }

        return "other";
    }
}