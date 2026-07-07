package searchengine.controller.plant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import searchengine.services.plant.TopicAnalysisService;

@RestController
@RequestMapping("/api/topics")
public class TopicAnalysisController {

    @Autowired
    private TopicAnalysisService analysisService;

    /**
     * Экспорт тем с найденными ключевыми словами
     * GET /api/topics/export-with-keywords
     */
    @GetMapping(value = "/export-with-keywords", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> exportTopicsWithKeywords(
            @RequestParam(defaultValue = "500") int limit) {

        String result = analysisService.exportTopicsWithKeywords(limit);

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=topics_with_keywords.txt")
                .body(result);
    }

    /**
     * Экспорт контента с частотами слов (второй файл)
     * GET /api/topics/export-content-frequencies
     */
    @GetMapping(value = "/export-content-frequencies", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> exportContentFrequencies(
            @RequestParam(defaultValue = "500") int limit) {

        String result = analysisService.exportContentWithFrequencies(limit);

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=content_only.txt")
                .body(result);
    }
}