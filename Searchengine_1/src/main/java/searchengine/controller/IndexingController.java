package searchengine.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.config.IndexingSettings;
import searchengine.config.IndexingState;
import searchengine.dto.response.IndexingResponse;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.PageProcessor;
import searchengine.services.SiteIndexingService;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api")
public class IndexingController {

    private static final Logger logger = LoggerFactory.getLogger(IndexingController.class);

    @Autowired
    private SiteIndexingService siteIndexingService;

    @Autowired
    private PageProcessor pageProcessor;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private PageRepository pageRepository;
    @Autowired
    private IndexingSettings indexingSettings;
    @Autowired
    private IndexingState indexingState;

    private final SiteIndexingService service;

    public IndexingController(SiteIndexingService service) {
        this.service = service;
    }

    /**
     * Запуск полной индексации всех сайтов
     *
     * @return Ответ с результатом запуска индексации
     */
    @GetMapping("/startIndexing")
    public ResponseEntity<Map<String, Object>> startIndexing() {
        try {
            Map<String, Object> response = service.startIndexing();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Ошибка в контроллере", e);
            return ResponseEntity.status(500).body(Map.of(
                    "result", false,
                    "error", "Internal Server Error"
            ));
        }
    }

    /**
     * Остановка индексации всех сайтов
     *
     * @return Ответ с результатом остановки индексации
     */
    @GetMapping("/stopIndexing")
    public ResponseEntity<IndexingResponse> stopIndexing() {
        logger.info("Запрос на остановку индексации...");
        try {
            boolean stopped = siteIndexingService.stopIndexing();
            if (stopped) {
                logger.info("Индексация успешно остановлена.");
                return ResponseEntity.ok(new IndexingResponse(true, "Индексация остановлена"));
            } else {
                logger.warn("Индексация не запущена.");
                return ResponseEntity.badRequest().body(new IndexingResponse(false, "Индексация не запущена"));
            }
        } catch (Exception e) {
            logger.error("Ошибка при остановке индексации: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(new IndexingResponse(false, "Ошибка при остановке индексации"));
        }
    }

    /**
     * Индексация отдельной страницы
     *
     * @param url URL страницы
     * @return Ответ с результатом индексации
     */
    @PostMapping("/indexPage")
    public ResponseEntity<Map<String, Object>> indexPage(@RequestParam String url) {
        if (indexingState.isIndexingInProgress()) {
            return ResponseEntity.status(500).body(Map.of(
                    "result", false,
                    "error", "Индексация уже запущена"
            ));
        }

        // Проверяем, что URL корректный
        if (!isValidUrl(url)) {
            return ResponseEntity.status(400).body(Map.of(
                    "result", false,
                    "error", "Некорректный URL"
            ));
        }

        // Находим сайт, к которому принадлежит страница
        Site site = getSiteByUrl(url);
        if (site == null) {
            return ResponseEntity.status(400).body(Map.of(
                    "result", false,
                    "error", "Страница не принадлежит ни одному из разрешенных сайтов"
            ));
        }

        try {
            // Устанавливаем статус индексации
            indexingState.setIndexingInProgress(true);

            // Удаляем предыдущие данные о странице
            pageProcessor.deletePageInfoIfExists(site, url);

            // Запускаем индексацию страницы
            pageProcessor.indexPage(site, url, 0);

            return ResponseEntity.ok(Map.of("result", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "result", false,
                    "error", "Ошибка индексации: " + e.getMessage()
            ));
        } finally {
            // Сбрасываем статус индексации
            indexingState.setIndexingInProgress(false);
        }
    }
    private Site getSiteByUrl(String url) {
        return indexingSettings.getSites().stream()
                .map(siteConfig -> siteRepository.findByUrl(siteConfig.getUrl())
                        .orElseGet(() -> {
                            Site newSite = new Site();
                            newSite.setUrl(siteConfig.getUrl());
                            newSite.setName(siteConfig.getName());
                            newSite.setStatus(Status.INDEXING);
                            return newSite;
                        }))
                .filter(s -> url.startsWith(s.getUrl()))
                .findFirst()
                .orElse(null);
    }
    private boolean isValidUrl(String url) {
        try {
            new URL(url);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }
}