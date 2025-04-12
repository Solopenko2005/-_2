package searchengine.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.response.IndexingResponse;
import searchengine.services.SiteIndexingService;
import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class IndexingController {
    private final SiteIndexingService siteIndexingService;

    @GetMapping("/startIndexing")
    public Map<String, Object> startIndexing() {
        return siteIndexingService.startIndexing().getBody();
    }

    @GetMapping("/stopIndexing")
    public IndexingResponse stopIndexing() {
        return siteIndexingService.stopIndexing().getBody();
    }

    @PostMapping("/indexPage")
    public Map<String, Object> indexPage(@RequestParam String url) {
        return siteIndexingService.indexPage(url).getBody();
    }
}