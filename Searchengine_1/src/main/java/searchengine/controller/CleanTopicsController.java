package searchengine.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.model.TopicGroup;
import searchengine.services.AdvancedTopicGroupingService;
import searchengine.services.EnhancedTopicFilterService;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/simple")
@RequiredArgsConstructor
public class CleanTopicsController {

    private final AdvancedTopicGroupingService groupingService;
    private final EnhancedTopicFilterService filterService;

    /**
     * Чистый топ тем (без мусора)
     * GET /api/simple/clean-top?limit=50
     */
    @GetMapping("/clean-top")
    public ResponseEntity<Map<String, Object>> getCleanTopTopics(
            @RequestParam(defaultValue = "50") int limit) {

        Map<String, Object> response = new HashMap<>();

        try {
            List<TopicGroup> allGroups = groupingService.getAllGroupsSorted();

            List<Map<String, ? extends Serializable>> cleanTopics = allGroups.stream()
                    .filter(filterService::isRelevantAndCleanTopic)
                    .sorted(Comparator.comparingInt(TopicGroup::getFrequency).reversed())
                    .limit(limit)
                    .map(group -> {
                        String cleanTitle = filterService.cleanTopicTitle(group.getTitle());

                        // Если после очистки заголовок слишком короткий, пропускаем
                        if (cleanTitle.length() < 10) {
                            return null;
                        }

                        return Map.of(
                                "Тема", cleanTitle,
                                "Частота", group.getFrequency(),
                                "Упоминания", group.getSiteCount(),
                                "ID группы", group.getId()
                        );
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            response.put("result", true);
            response.put("data", cleanTopics);
            response.put("count", cleanTopics.size());
            response.put("message", "Темы очищены от мусорных данных");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("result", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Экспорт в текстовый формат
     * GET /api/simple/export-text
     */
    @GetMapping(value = "/export-text", produces = "text/plain")
    public ResponseEntity<String> exportTopicsText(
            @RequestParam(defaultValue = "50") int limit) {

        StringBuilder text = new StringBuilder();
        text.append("ТОП-").append(limit).append(" ТЕМ ПО ЧАСТОТЕ УПОМИНАНИЙ\n");
        text.append("========================================\n\n");

        List<TopicGroup> allGroups = groupingService.getAllGroupsSorted();

        int counter = 1;
        for (TopicGroup group : allGroups) {
            if (counter > limit) break;

            if (filterService.isRelevantAndCleanTopic(group)) {
                String cleanTitle = filterService.cleanTopicTitle(group.getTitle());

                if (cleanTitle.length() >= 10) {
                    text.append(counter).append(". ")
                            .append(cleanTitle).append("\n")
                            .append("   Частота: ").append(group.getFrequency())
                            .append(" | Упоминания на сайтах: ").append(group.getSiteCount())
                            .append("\n\n");
                    counter++;
                }
            }
        }

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=top_topics.txt")
                .body(text.toString());
    }
}