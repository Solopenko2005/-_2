package searchengine.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.response.SearchResponse;
import searchengine.services.SearchService;

@RestController
@RequestMapping("/api")
public class SearchController {

    private final SearchService searchService;

    @Autowired
    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    public SearchResponse search(
            @RequestParam String query,
            @RequestParam(required = false) String site,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit) {
        if (site != null && !site.isEmpty()) {
            return searchService.search(query, site, offset, limit); // Поиск по конкретному сайту
        } else {
            return searchService.search(query, "", offset, limit); // Поиск по всем сайтам
        }
    }
}