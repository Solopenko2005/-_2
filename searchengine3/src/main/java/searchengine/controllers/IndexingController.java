package searchengine.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import searchengine.model.Site;
import searchengine.repository.SiteRepository;
import searchengine.services.PageProcessor;
import searchengine.services.SiteIndexingService;

import java.util.Map;

@RestController
public class IndexingController {

    private final SiteIndexingService indexingService;
    private static final Logger logger = LoggerFactory.getLogger(IndexingController.class);

    @Autowired
    public IndexingController(SiteIndexingService indexingService) {
        this.indexingService = indexingService;
    }
    @Autowired
    private PageProcessor pageProcessor;

    @Autowired
    private SiteRepository siteRepository;
    @PostMapping("/indexPage")
    public ResponseEntity<?> indexPage(@RequestParam String url) {
        try {
            Site site = siteRepository.findSiteByUrl(url);
            if (site == null) {
                return ResponseEntity.badRequest().body("Ошибка: сайт не найден");
            }

            // Используем site.getId(), а не Integer.parseInt(site.getUrl())
            pageProcessor.parsePageAndSaveEntitiesToDB(url, site.getId());

            return ResponseEntity.ok("Страница успешно проиндексирована");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка индексации: " + e.getMessage());
        }
    }


    @GetMapping("/api/startIndexing")
    public Map<String, Object> startIndexing() {
        logger.info("Запуск индексации...");
        if (indexingService.startIndexing()) {
            logger.info("Индексация успешно запущена.");
            return Map.of("result", true);
        } else {
            logger.warn("Индексация уже запущена.");
            return Map.of("result", false, "error", "Индексация уже запущена");
        }
    }

    @GetMapping("/api/stopIndexing")
    public Map<String, Object> stopIndexing() {
        logger.info("Остановка индексации...");
        if (indexingService.stopIndexing()) {
            logger.info("Индексация успешно остановлена.");
            return Map.of("result", true);
        } else {
            logger.warn("Индексация не запущена.");
            return Map.of("result", false, "error", "Индексация не запущена");
        }
    }
}
