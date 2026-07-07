package searchengine.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.services.TopicGroupingService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/topics")
@RequiredArgsConstructor
public class TopicGroupAdminController {

    private final TopicGroupingService topicGroupingService;

    /**
     * Запустить группировку всех тем
     * POST /api/admin/topics/group
     */
    @PostMapping("/group")
    public ResponseEntity<Map<String, Object>> groupAllTopics() {
        Map<String, Object> response = new HashMap<>();

        try {
            topicGroupingService.groupAllTopics();

            response.put("result", true);
            response.put("message", "Группировка тем успешно запущена");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("result", false);
            response.put("error", "Ошибка при группировке тем: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Получить статистику по группам
     * GET /api/admin/topics/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getGroupStats() {
        Map<String, Object> response = new HashMap<>();

        try {
            Map<String, Object> stats = topicGroupingService.getGroupStatistics();

            response.put("result", true);
            response.put("statistics", stats);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("result", false);
            response.put("error", "Не удалось получить статистику: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}