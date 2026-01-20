package searchengine.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.model.TopicGroup;
import searchengine.repository.TopicRepository;
import searchengine.services.AdvancedTopicGroupingService;
import searchengine.services.TopicFilterService;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/simple")
@RequiredArgsConstructor
public class SimpleTopicController {

    private final AdvancedTopicGroupingService groupingService;
    private final TopicFilterService filterService;
    private final TopicRepository topicRepository;

    /**
     * Простой вывод: тема, частота, упоминания
     * GET /api/simple/topics?limit=20
     */
    @GetMapping("/topics")
    public ResponseEntity<Map<String, Object>> getSimpleTopicsList(
            @RequestParam(defaultValue = "20") int limit) {

        Map<String, Object> response = new HashMap<>();

        try {
            List<TopicGroup> allGroups = groupingService.getAllGroupsSorted();

            List<Map<String, Object>> topicsList = allGroups.stream()
                    .filter(group -> group.getFrequency() >= 2)
                    .filter(filterService::isRelevantTopic)
                    .limit(limit)
                    .map(group -> {
                        // Получаем уникальные сайты
                        List<String> sites = topicRepository.findByTopicGroupId(group.getId())
                                .stream()
                                .map(topic -> topic.getSite().getName())
                                .distinct()
                                .collect(Collectors.toList());

                        return Map.of(
                                "theme", group.getTitle(),
                                "frequency", group.getFrequency(),
                                "mentions", group.getSiteCount(), // число сайтов
                                "sites", sites,
                                "groupId", group.getId()
                        );
                    })
                    .collect(Collectors.toList());

            response.put("result", true);
            response.put("data", topicsList);
            response.put("count", topicsList.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("result", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Вывод в формате таблицы
     * GET /api/simple/table?limit=50
     */
    @GetMapping("/table")
    public ResponseEntity<Map<String, Object>> getTopicsTable(
            @RequestParam(defaultValue = "50") int limit) {

        Map<String, Object> response = new HashMap<>();

        try {
            List<TopicGroup> allGroups = groupingService.getAllGroupsSorted();

            List<Map<String, Object>> tableData = allGroups.stream()
                    .filter(group -> group.getFrequency() >= 2)
                    .filter(filterService::isRelevantTopic)
                    .limit(limit)
                    .map(group -> {
                        // Получаем оригинальные названия тем
                        List<String> originalTitles = topicRepository.findByTopicGroupId(group.getId())
                                .stream()
                                .map(topic -> topic.getTitle())
                                .distinct()
                                .collect(Collectors.toList());

                        return Map.of(
                                "№", 0, // будет заполнено на фронтенде
                                "Тема", group.getTitle(),
                                "Частота", group.getFrequency(),
                                "Упоминания", group.getSiteCount(),
                                "Оригинальные названия", originalTitles,
                                "groupId", group.getId()
                        );
                    })
                    .collect(Collectors.toList());

            response.put("result", true);
            response.put("columns", Arrays.asList(
                    "№", "Тема", "Частота", "Упоминания", "Оригинальные названия"
            ));
            response.put("data", tableData);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("result", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}