package searchengine.controller;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import searchengine.dto.response.IndexingResponse;
import searchengine.services.SiteIndexingService;

import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class IndexingController {
    private static final Logger logger = LoggerFactory.getLogger(IndexingController.class);
    private final SiteIndexingService siteIndexingService;

    @GetMapping("/startIndexing")
    public ResponseEntity<Map<String, Object>> startIndexing() {
        return siteIndexingService.startIndexing();
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<IndexingResponse> stopIndexing() {
        return siteIndexingService.stopIndexing();
    }

    @PostMapping("/indexPage")
    public ResponseEntity<Map<String, Object>> indexPage(@RequestParam String url) {
        return siteIndexingService.indexPage(url);
    }
}