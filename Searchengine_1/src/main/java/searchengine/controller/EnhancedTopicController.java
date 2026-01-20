package searchengine.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.TopicStatsDto;
import searchengine.dto.TopicReferenceDto;
import searchengine.model.Topic;
import searchengine.model.TopicGroup;
import searchengine.repository.TopicRepository;
import searchengine.services.AdvancedTopicGroupingService;
import searchengine.services.TopicFilterService;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/topics")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EnhancedTopicController {

    private final AdvancedTopicGroupingService groupingService;
    private final TopicFilterService filterService;
    private final TopicRepository topicRepository;

    /**
     * Получить темы со статистикой: тема, частота, число упоминаний
     * GET /api/topics/stats?limit=50&minFrequency=2&sortBy=frequency
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getTopicsWithStats(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "2") int minFrequency,
            @RequestParam(defaultValue = "1") int minSiteCount,
            @RequestParam(defaultValue = "frequency") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder) {

        Map<String, Object> response = new HashMap<>();

        try {
            // Получаем все группы
            List<TopicGroup> allGroups = groupingService.getAllGroupsSorted();

            // Фильтруем и сортируем
            List<TopicGroup> filteredGroups = allGroups.stream()
                    .filter(group -> group.getFrequency() >= minFrequency)
                    .filter(group -> group.getSiteCount() >= minSiteCount)
                    .filter(filterService::isRelevantTopic)
                    .sorted(getComparator(sortBy, sortOrder))
                    .limit(limit)
                    .collect(Collectors.toList());

            // Конвертируем в нужный формат
            List<TopicStatsDto> result = filteredGroups.stream()
                    .map(this::convertToTopicStatsDto)
                    .collect(Collectors.toList());

            // Статистика
            Map<String, Object> statistics = calculateStatistics(result);

            response.put("result", true);
            response.put("data", result);
            response.put("count", result.size());
            response.put("statistics", statistics);
            response.put("filters", Map.of(
                    "limit", limit,
                    "minFrequency", minFrequency,
                    "minSiteCount", minSiteCount,
                    "sortBy", sortBy,
                    "sortOrder", sortOrder
            ));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("result", false);
            response.put("error", "Ошибка: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Получить детальную статистику по конкретной теме
     * GET /api/topics/stats/{groupId}
     */
    @GetMapping("/stats/{groupId}")
    public ResponseEntity<Map<String, Object>> getTopicStatsDetails(@PathVariable int groupId) {
        Map<String, Object> response = new HashMap<>();

        try {
            TopicGroup group = groupingService.getGroupById(groupId);

            if (group == null) {
                response.put("result", false);
                response.put("error", "Группа тем не найдена");
                return ResponseEntity.status(404).body(response);
            }

            // Получаем все темы группы
            List<Topic> topics = topicRepository.findByTopicGroupId(groupId);

            // Конвертируем в DTO
            TopicStatsDto dto = convertToTopicStatsDto(group);

            // Дополнительная статистика
            Map<String, Object> details = new HashMap<>();
            details.put("themeStats", dto);
            details.put("totalMentions", topics.size());

            // Распределение по сайтам
            Map<String, Long> distributionBySite = topics.stream()
                    .collect(Collectors.groupingBy(
                            topic -> topic.getSite().getName(),
                            Collectors.counting()
                    ));

            details.put("distributionBySite", distributionBySite);

            // Распределение по датам
            Map<String, Long> distributionByDate = topics.stream()
                    .collect(Collectors.groupingBy(
                            topic -> topic.getCreatedAt().toLocalDate().toString(),
                            Collectors.counting()
                    ));

            details.put("distributionByDate", distributionByDate);

            response.put("result", true);
            response.put("data", details);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("result", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Поиск тем по названию с статистикой
     * GET /api/topics/stats/search?query=трактор&limit=10
     */
    @GetMapping("/stats/search")
    public ResponseEntity<Map<String, Object>> searchTopicsWithStats(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit) {

        Map<String, Object> response = new HashMap<>();

        try {
            // Ищем группы по запросу
            List<TopicGroup> groups = groupingService.searchGroups(query, limit * 2);

            List<TopicStatsDto> result = groups.stream()
                    .filter(filterService::isRelevantTopic)
                    .limit(limit)
                    .map(this::convertToTopicStatsDto)
                    .collect(Collectors.toList());

            response.put("result", true);
            response.put("query", query);
            response.put("data", result);
            response.put("count", result.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("result", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Конвертация группы в DTO статистики
     */
    private TopicStatsDto convertToTopicStatsDto(TopicGroup group) {
        TopicStatsDto dto = new TopicStatsDto();

        // Основная тема (берем заголовок группы)
        dto.setTheme(group.getTitle());

        // Частота использования
        dto.setFrequency(group.getFrequency());

        // Число упоминаний (сайтов)
        dto.setSiteCount(group.getSiteCount());

        // Получаем все темы группы
        List<Topic> topics = topicRepository.findByTopicGroupId(group.getId());

        // Оригинальные названия тем
        List<String> originalTitles = topics.stream()
                .map(Topic::getTitle)
                .distinct()
                .collect(Collectors.toList());
        dto.setOriginalTitles(originalTitles);

        // Названия сайтов
        List<String> siteNames = topics.stream()
                .map(topic -> topic.getSite().getName())
                .distinct()
                .collect(Collectors.toList());
        dto.setSiteNames(siteNames);

        // Ссылки на упоминания
        List<TopicReferenceDto> references = topics.stream()
                .map(this::convertToReferenceDto)
                .collect(Collectors.toList());
        dto.setReferences(references);

        return dto;
    }

    /**
     * Конвертация темы в DTO ссылки
     */
    private TopicReferenceDto convertToReferenceDto(Topic topic) {
        TopicReferenceDto ref = new TopicReferenceDto();

        // Оригинальное название
        ref.setOriginalTitle(topic.getTitle());

        // Название страницы (можно использовать заголовок темы или content)
        ref.setPageTitle(topic.getTitle());

        // Информация о сайте
        if (topic.getSite() != null) {
            ref.setSiteName(topic.getSite().getName());

            // Формируем URL
            String siteUrl = topic.getSite().getUrl();
            String pagePath = topic.getPage() != null ? topic.getPage().getPath() : "";
            ref.setUrl(constructFullUrl(siteUrl, pagePath));
        }

        return ref;
    }

    /**
     * Компаратор для сортировки
     */
    private Comparator<TopicGroup> getComparator(String sortBy, String sortOrder) {
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

        if ("desc".equalsIgnoreCase(sortOrder)) {
            comparator = comparator.reversed();
        }

        return comparator;
    }

    /**
     * Расчет статистики
     */
    private Map<String, Object> calculateStatistics(List<TopicStatsDto> dtos) {
        Map<String, Object> stats = new HashMap<>();

        if (dtos.isEmpty()) {
            return stats;
        }

        // Базовая статистика
        int totalFrequency = dtos.stream().mapToInt(TopicStatsDto::getFrequency).sum();
        int totalSiteCount = dtos.stream().mapToInt(TopicStatsDto::getSiteCount).sum();
        int totalReferences = dtos.stream()
                .mapToInt(dto -> dto.getReferences() != null ? dto.getReferences().size() : 0)
                .sum();

        stats.put("totalThemes", dtos.size());
        stats.put("totalFrequency", totalFrequency);
        stats.put("totalSiteCount", totalSiteCount);
        stats.put("totalReferences", totalReferences);

        // Средние значения
        stats.put("avgFrequency", (double) totalFrequency / dtos.size());
        stats.put("avgSiteCount", (double) totalSiteCount / dtos.size());
        stats.put("avgReferencesPerTheme", (double) totalReferences / dtos.size());

        // Топ тем
        List<Map<String, ? extends Serializable>> topThemes = dtos.stream()
                .limit(5)
                .map(dto -> Map.of(
                        "theme", dto.getTheme(),
                        "frequency", dto.getFrequency(),
                        "siteCount", dto.getSiteCount()
                ))
                .collect(Collectors.toList());

        stats.put("topThemes", topThemes);

        return stats;
    }

    /**
     * Построение полного URL
     */
    private String constructFullUrl(String siteUrl, String pagePath) {
        if (siteUrl == null) siteUrl = "";
        if (pagePath == null) pagePath = "";

        siteUrl = siteUrl.trim();
        pagePath = pagePath.trim();

        if (siteUrl.endsWith("/") && pagePath.startsWith("/")) {
            siteUrl = siteUrl.substring(0, siteUrl.length() - 1);
        } else if (!siteUrl.endsWith("/") && !pagePath.startsWith("/") && !pagePath.isEmpty()) {
            siteUrl = siteUrl + "/";
        }

        return siteUrl + pagePath;
    }
}