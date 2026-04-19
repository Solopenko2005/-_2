package searchengine.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.model.Topic;
import searchengine.model.TopicGroup;
import searchengine.repository.TopicRepository;
import searchengine.services.AdvancedTopicGroupingService;
import searchengine.services.EnhancedTopicFilterService;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/topics/seed-breeding")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class SeedBreedingTechController {

    private final AdvancedTopicGroupingService groupingService;
    private final EnhancedTopicFilterService enhancedFilterService;
    private final TopicRepository topicRepository;

    // === Ключевые слова: семеноводство и селекция ===
    private static final List<String> SEED_BREEDING_KEYWORDS = Arrays.asList(
            "семеноводство", "селекция", "селекция растений", "производство семян",
            "качество семян", "всхожесть семян", "гибридные семена", "обработка семян",
            "молекулярная селекция", "генетическое улучшение", "технология семян",
            "генетика растений", "хранение семян", "сертификация семян",
            "традиционная селекция", "сортоиспытание", "семенной материал",
            "репродукция семян", "элитные семена", "семенной контроль"
    );

    // === Технологические маркеры ===
    private static final List<String> TECH_MARKERS_RU = Arrays.asList(
            "технология", "метод", "система", "подход", "методика", "инструмент",
            "платформа", "модель", "алгоритм", "ген", "редактирование", "маркер",
            "секвенирование", "геномный", "молекулярный", "селекция", "гибрид",
            "трансформация", "консервация", "обработка", "анализ", "криспр",
            "геномное редактирование", "маркер-вспомогательная селекция",
            "мас-селекция", "геномная селекция", "биоинформатика",
            "фенотипирование", "генотипирование"
    );

    /**
     * Поиск тем: семеноводство/селекция + технологические маркеры
     * GET /api/topics/seed-breeding/tech?limit=50&minFrequency=2&sortBy=frequency&includeContent=false
     */
    @GetMapping("/tech")
    public ResponseEntity<Map<String, Object>> getSeedBreedingTechTopics(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "2") int minFrequency,
            @RequestParam(defaultValue = "frequency") String sortBy,
            @RequestParam(defaultValue = "false") boolean includeContent) {

        Map<String, Object> response = new HashMap<>();

        try {
            long startTime = System.currentTimeMillis();
            List<TopicGroup> allGroups = groupingService.getAllGroupsSorted();

            // Фильтрация: семеноводство/селекция + технологии
            List<TopicGroup> filteredGroups = allGroups.stream()
                    .filter(group -> group.getFrequency() >= minFrequency)
                    .filter(this::matchesSeedBreedingKeywords)
                    .filter(this::containsTechMarker)
                    .filter(enhancedFilterService::isRelevantAndCleanTopic)
                    .sorted(getComparator(sortBy))
                    .limit(limit)
                    .collect(Collectors.toList());

            List<Map<String, Object>> topicsList = filteredGroups.stream()
                    .map(group -> buildTopicResponse(group, includeContent))
                    .collect(Collectors.toList());

            // Статистика
            long matchedSeed = allGroups.stream().filter(this::matchesSeedBreedingKeywords).count();
            long withTech = allGroups.stream()
                    .filter(this::matchesSeedBreedingKeywords)
                    .filter(this::containsTechMarker)
                    .count();

            Map<String, Object> statistics = new HashMap<>();
            statistics.put("totalGroupsScanned", allGroups.size());
            statistics.put("matchedSeedBreedingKeywords", matchedSeed);
            statistics.put("withTechMarkers", withTech);
            statistics.put("afterAllFilters", filteredGroups.size());
            statistics.put("minFrequency", minFrequency);
            statistics.put("executionTimeMs", System.currentTimeMillis() - startTime);

            if (!filteredGroups.isEmpty()) {
                statistics.put("avgFrequency", filteredGroups.stream()
                        .mapToInt(TopicGroup::getFrequency)
                        .average().orElse(0));
                statistics.put("topFrequency", filteredGroups.get(0).getFrequency());
            }

            response.put("result", true);
            response.put("data", topicsList);
            response.put("count", topicsList.size());
            response.put("statistics", statistics);
            response.put("filters", Map.of(
                    "seedBreedingKeywordsCount", SEED_BREEDING_KEYWORDS.size(),
                    "techMarkersCount", TECH_MARKERS_RU.size(),
                    "minFrequency", minFrequency,
                    "sortBy", sortBy
            ));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Ошибка при поиске тем семеноводство/селекция+технологии: {}", e.getMessage(), e);
            response.put("result", false);
            response.put("error", "Ошибка: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Проверка: содержит ли тема ключевые слова семеноводства/селекции
     * Использует title + contentSummary + контент из связанных Topic
     */
    private boolean matchesSeedBreedingKeywords(TopicGroup group) {
        String searchableText = normalizeForSearch(buildSearchableText(group));

        return SEED_BREEDING_KEYWORDS.stream()
                .anyMatch(kw -> {
                    String normalizedKw = normalizeForSearch(kw);
                    return searchableText.contains(normalizedKw) ||
                            matchesWordForm(searchableText, normalizedKw);
                });
    }
    /**
     * Нормализация текста для поиска:
     * - нижний регистр
     * - удаление знаков препинания (кроме дефиса для составных слов)
     * - замена множественных пробелов на один
     */
    private String normalizeForSearch(String text) {
        if (text == null) return "";
        return text.toLowerCase()
                .replaceAll("[^а-яё\\-\\s]", " ")  // оставляем только буквы, дефис и пробелы
                .replaceAll("\\s+", " ")
                .trim();
    }
    /**
     * Простая проверка словоформ:
     * если ключевое слово "селекция", найдёт "селекции", "селекцией" и т.д.
     */
    private boolean matchesWordForm(String text, String keyword) {
        // Если ключевое слово уже найдено — не нужно проверять
        if (text.contains(keyword)) return true;

        // Для русских слов: проверяем, начинается ли слово в тексте с основы ключевого слова
        // Пример: "селекци" найдёт "селекция", "селекции", "селекцией"
        String root = keyword.length() > 4 ? keyword.substring(0, keyword.length() - 2) : keyword;

        // Разбиваем текст на слова и проверяем каждое
        String[] words = text.split("\\s+");
        for (String word : words) {
            if (word.startsWith(root) && word.length() >= keyword.length() - 2) {
                return true;
            }
        }
        return false;
    }


    /**
     * Проверка: содержит ли тема технологические маркеры
     */
    private boolean containsTechMarker(TopicGroup group) {
        String searchableText = normalizeForSearch(buildSearchableText(group));

        return TECH_MARKERS_RU.stream()
                .anyMatch(marker -> {
                    String normalizedMarker = normalizeForSearch(marker);
                    return searchableText.contains(normalizedMarker) ||
                            matchesWordForm(searchableText, normalizedMarker);
                });
    }
    /**
     * Сбор текста для поиска: title + contentSummary + контент из Topic
     */
    private String buildSearchableText(TopicGroup group) {
        StringBuilder sb = new StringBuilder();

        // Добавляем заголовок
        if (group.getTitle() != null) {
            sb.append(group.getTitle().toLowerCase()).append(" ");
        }

        // Добавляем contentSummary
        if (group.getContentSummary() != null) {
            sb.append(group.getContentSummary().toLowerCase()).append(" ");
        }

        // Добавляем ключевые леммы
        if (group.getKeyLemmas() != null) {
            group.getKeyLemmas().forEach(lemma ->
                    sb.append(lemma.toLowerCase()).append(" "));
        }

        // Добавляем контент из первых 3 связанных Topic (для точности)
        if (group.getTopics() != null && !group.getTopics().isEmpty()) {
            group.getTopics().stream()
                    .limit(3)
                    .map(Topic::getContent)
                    .filter(Objects::nonNull)
                    .forEach(content ->
                            sb.append(content.toLowerCase()).append(" "));
        }

        return sb.toString();
    }

    /**
     * Построение ответа для одной темы
     */
    private Map<String, Object> buildTopicResponse(TopicGroup group, boolean includeContent) {
        Map<String, Object> topicInfo = new LinkedHashMap<>();

        String cleanTitle = enhancedFilterService.cleanTopicTitle(group.getTitle());

        topicInfo.put("id", group.getId());
        topicInfo.put("theme", cleanTitle.length() > 120 ?
                cleanTitle.substring(0, 120) + "..." : cleanTitle);
        topicInfo.put("frequency", group.getFrequency());
        topicInfo.put("siteCount", group.getSiteCount());

        // Найденные ключевые слова
        String searchableText = buildSearchableText(group);
        topicInfo.put("matchedSeedKeywords", findMatchedKeywords(searchableText, SEED_BREEDING_KEYWORDS));
        topicInfo.put("matchedTechMarkers", findMatchedKeywords(searchableText, TECH_MARKERS_RU));

        // Список сайтов
        List<String> sites = group.getTopics().stream()
                .map(topic -> topic.getSite() != null ? topic.getSite().getName() : null)
                .filter(Objects::nonNull)
                .distinct()
                .limit(5)
                .collect(Collectors.toList());
        topicInfo.put("sites", sites);

        // Детали при запросе
        if (includeContent && group.getContentSummary() != null) {
            topicInfo.put("contentSummary", group.getContentSummary().length() <= 500
                    ? group.getContentSummary()
                    : group.getContentSummary().substring(0, 500) + "...");

            // Пример первой связанной страницы
            if (!group.getTopics().isEmpty()) {
                Topic sample = group.getTopics().iterator().next();
                topicInfo.put("samplePage", Map.of(
                        "title", sample.getTitle(),
                        "path", sample.getPage() != null ? sample.getPage().getPath() : null
                ));
            }
        }

        // Мета-информация
        topicInfo.put("keyLemmas", group.getKeyLemmas() != null ?
                new ArrayList<>(group.getKeyLemmas()).subList(0,
                        Math.min(10, group.getKeyLemmas().size())) : List.of());
        topicInfo.put("originalTitle", group.getTitle());
        topicInfo.put("cleaned", !cleanTitle.equals(group.getTitle()));

        return topicInfo;
    }

    /**
     * Поиск совпавших ключевых слов в тексте
     */
    private List<String> findMatchedKeywords(String text, List<String> keywords) {
        String lowerText = text.toLowerCase();
        return keywords.stream()
                .filter(kw -> lowerText.contains(kw.toLowerCase()))
                .distinct()
                .limit(5)
                .collect(Collectors.toList());
    }

    /**
     * Компаратор для сортировки
     */
    private Comparator<TopicGroup> getComparator(String sortBy) {
        return switch (sortBy.toLowerCase()) {
            case "frequency" -> Comparator.comparingInt(TopicGroup::getFrequency);
            case "sitecount", "mentions" -> Comparator.comparingInt(TopicGroup::getSiteCount);
            case "title", "theme" -> Comparator.comparing(TopicGroup::getTitle, String.CASE_INSENSITIVE_ORDER);
            case "relevance" -> Comparator
                    .comparingInt((TopicGroup g) -> countMatchedKeywords(buildSearchableText(g), SEED_BREEDING_KEYWORDS))
                    .thenComparingInt(g -> countMatchedKeywords(buildSearchableText(g), TECH_MARKERS_RU))
                    .thenComparingInt(TopicGroup::getFrequency);
            default -> Comparator.comparingInt(TopicGroup::getFrequency);
        };
    }

    /**
     * Подсчёт совпадений ключевых слов
     */
    private int countMatchedKeywords(String text, List<String> keywords) {
        return (int) keywords.stream()
                .filter(kw -> text.contains(kw.toLowerCase()))
                .count();
    }

    /**
     * Все темы по семеноводству/селекции (без обязательных тех-маркеров)
     * GET /api/topics/seed-breeding/all?limit=30
     */
    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> getAllSeedBreedingTopics(
            @RequestParam(defaultValue = "30") int limit,
            @RequestParam(defaultValue = "2") int minFrequency) {

        Map<String, Object> response = new HashMap<>();

        try {
            List<TopicGroup> allGroups = groupingService.getAllGroupsSorted();

            List<Map<String, Object>> topicsList = allGroups.stream()
                    .filter(g -> g.getFrequency() >= minFrequency)
                    .filter(this::matchesSeedBreedingKeywords)
                    .filter(enhancedFilterService::isRelevantAndCleanTopic)
                    .sorted(Comparator.comparingInt(TopicGroup::getFrequency).reversed())
                    .limit(limit)
                    .map(group -> {
                        String searchableText = buildSearchableText(group);
                        Map<String, Object> info = new LinkedHashMap<>();
                        info.put("id", group.getId());
                        info.put("theme", enhancedFilterService.cleanTopicTitle(group.getTitle()));
                        info.put("frequency", group.getFrequency());
                        info.put("siteCount", group.getSiteCount());
                        info.put("hasTechMarkers", containsTechMarker(group));
                        info.put("matchedKeywords", findMatchedKeywords(searchableText, SEED_BREEDING_KEYWORDS));
                        info.put("keyLemmas", group.getKeyLemmas() != null ?
                                new ArrayList<>(group.getKeyLemmas()).subList(0, Math.min(5, group.getKeyLemmas().size())) : List.of());
                        return info;
                    })
                    .collect(Collectors.toList());

            response.put("result", true);
            response.put("data", topicsList);
            response.put("count", topicsList.size());
            response.put("note", "Включает все темы по семеноводству/селекции. Используйте /tech для фильтрации по технологиям.");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("result", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Получение списков ключевых слов
         * GET /api/topics/seed-breeding/keywords
     */
    @GetMapping("/keywords")
    public ResponseEntity<Map<String, Object>> getKeywordLists() {
        Map<String, Object> response = new HashMap<>();
        response.put("result", true);
        response.put("seedBreedingKeywords", SEED_BREEDING_KEYWORDS);
        response.put("techMarkers", TECH_MARKERS_RU);
        response.put("logic", "Тема считается релевантной, если содержит ≥1 слово из seedBreedingKeywords " +
                "И ≥1 слово из techMarkers. Поиск ведётся по: title + contentSummary + keyLemmas + content из Topic");
        return ResponseEntity.ok(response);
    }

    /**
     * Детальная информация по конкретной группе тем
     * GET /api/topics/seed-breeding/group/{groupId}
     */
    @GetMapping("/group/{groupId}")
    public ResponseEntity<Map<String, Object>> getGroupDetails(@PathVariable int groupId) {
        Map<String, Object> response = new HashMap<>();

        try {
            TopicGroup group = groupingService.getAllGroupsSorted().stream()
                    .filter(g -> g.getId() == groupId)
                    .findFirst()
                    .orElse(null);

            if (group == null) {
                response.put("result", false);
                response.put("error", "Группа не найдена");
                return ResponseEntity.notFound().build();
            }

            String searchableText = buildSearchableText(group);
            boolean isSeedBreeding = matchesSeedBreedingKeywords(group);
            boolean hasTech = containsTechMarker(group);

            List<Map<String, ? extends Serializable>> topicsDetails = group.getTopics().stream()
                    .limit(10)
                    .map(topic -> Map.of(
                            "id", topic.getId(),
                            "title", topic.getTitle(),
                            "site", topic.getSite() != null ? topic.getSite().getName() : null,
                            "lemmaCount", topic.getLemmaCount()
                    ))
                    .collect(Collectors.toList());

            response.put("result", true);
            response.put("group", Map.of(
                    "id", group.getId(),
                    "title", group.getTitle(),
                    "contentSummary", group.getContentSummary(),
                    "frequency", group.getFrequency(),
                    "siteCount", group.getSiteCount(),
                    "keyLemmas", group.getKeyLemmas()
            ));
            response.put("analysis", Map.of(
                    "isSeedBreedingRelated", isSeedBreeding,
                    "hasTechMarkers", hasTech,
                    "matchedSeedKeywords", findMatchedKeywords(searchableText, SEED_BREEDING_KEYWORDS),
                    "matchedTechMarkers", findMatchedKeywords(searchableText, TECH_MARKERS_RU)
            ));
            response.put("topics", topicsDetails);
            response.put("totalTopicsInGroup", group.getTopics().size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("result", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    /**
     * DEBUG: Показать, что ищется и почему не находится
     * GET /api/topics/seed-breeding/debug?groupId=123&keyword=селекция
     */
    @GetMapping("/debug")
    public ResponseEntity<Map<String, Object>> debugSearch(
            @RequestParam(required = false) Integer groupId,
            @RequestParam(required = false) String keyword) {

        Map<String, Object> response = new HashMap<>();

        try {
            List<TopicGroup> allGroups = groupingService.getAllGroupsSorted();

            // Если указан конкретный groupId — покажем детали
            if (groupId != null) {
                TopicGroup group = allGroups.stream()
                        .filter(g -> g.getId() == groupId)
                        .findFirst()
                        .orElse(null);

                if (group == null) {
                    response.put("result", false);
                    response.put("error", "Группа с id=" + groupId + " не найдена");
                    return ResponseEntity.badRequest().body(response);
                }

                String searchableText = buildSearchableText(group);

                response.put("result", true);
                response.put("groupId", groupId);
                response.put("title", group.getTitle());
                response.put("contentSummary", group.getContentSummary());
                response.put("keyLemmas", group.getKeyLemmas());
                response.put("topicsCount", group.getTopics().size());
                response.put("searchableText", searchableText);
                response.put("searchableTextLength", searchableText.length());

                // Проверка по всем ключевым словам
                Map<String, Boolean> seedMatches = new HashMap<>();
                Map<String, Boolean> techMatches = new HashMap<>();

                for (String kw : SEED_BREEDING_KEYWORDS) {
                    seedMatches.put(kw, searchableText.contains(kw.toLowerCase()));
                }
                for (String marker : TECH_MARKERS_RU) {
                    techMatches.put(marker, searchableText.contains(marker.toLowerCase()));
                }

                response.put("seedKeywordMatches", seedMatches);
                response.put("techMarkerMatches", techMatches);

                return ResponseEntity.ok(response);
            }

            // Если keyword указан — найдём группы, где он встречается
            if (keyword != null && !keyword.isEmpty()) {
                String lowerKeyword = keyword.toLowerCase();

                List<Map<String, ? extends Serializable>> found = allGroups.stream()
                        .filter(g -> buildSearchableText(g).contains(lowerKeyword))
                        .limit(20)
                        .map(g -> Map.of(
                                "id", g.getId(),
                                "title", g.getTitle(),
                                "frequency", g.getFrequency(),
                                "contentSummaryPreview", g.getContentSummary() != null ?
                                        g.getContentSummary().substring(0, Math.min(100, g.getContentSummary().length())) : null
                        ))
                        .collect(Collectors.toList());

                response.put("result", true);
                response.put("searchKeyword", keyword);
                response.put("foundInGroups", found.size());
                response.put("samples", found);

                return ResponseEntity.ok(response);
            }

            // Общий обзор: какие слова из наших списков вообще встречаются в базе
            Map<String, Integer> foundSeedKeywords = new HashMap<>();
            Map<String, Integer> foundTechMarkers = new HashMap<>();

            for (TopicGroup group : allGroups) {
                String text = buildSearchableText(group);

                for (String kw : SEED_BREEDING_KEYWORDS) {
                    if (text.contains(kw.toLowerCase())) {
                        foundSeedKeywords.put(kw, foundSeedKeywords.getOrDefault(kw, 0) + 1);
                    }
                }
                for (String marker : TECH_MARKERS_RU) {
                    if (text.contains(marker.toLowerCase())) {
                        foundTechMarkers.put(marker, foundTechMarkers.getOrDefault(marker, 0) + 1);
                    }
                }
            }

            response.put("result", true);
            response.put("totalGroups", allGroups.size());
            response.put("foundSeedKeywords",
                    foundSeedKeywords.entrySet().stream()
                            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                            .limit(15)
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            response.put("foundTechMarkers",
                    foundTechMarkers.entrySet().stream()
                            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                            .limit(15)
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            response.put("note", "Показаны только слова, которые реально найдены в базе. Если списки пустые — проверьте контент.");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("result", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}