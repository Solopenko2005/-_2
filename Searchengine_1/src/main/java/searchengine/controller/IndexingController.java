package searchengine.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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
import java.util.Optional;

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

    /**
     * Запуск индексации всех сайтов
     *
     * @return Ответ с результатом запуска индексации
     */
    @GetMapping("/startIndexing")
    public ResponseEntity<Map<String, Object>> startIndexing() {
        logger.info("Запрос на запуск индексации...");
        boolean started = siteIndexingService.startIndexing();
        if (started) {
            logger.info("Индексация успешно запущена.");
            return ResponseEntity.ok(Map.of("result", true, "message", "Индексация запущена"));
        } else {
            logger.warn("Индексация уже запущена.");
            return ResponseEntity.badRequest().body(Map.of("result", false, "error", "Индексация уже запущена"));
        }
    }

    /**
     * Остановка индексации всех сайтов
     *
     * @return Ответ с результатом остановки индексации
     */
    @GetMapping("/stopIndexing")
    public ResponseEntity<Map<String, Object>> stopIndexing() {
        logger.info("Запрос на остановку индексации...");
        boolean stopped = siteIndexingService.stopIndexing();
        if (stopped) {
            logger.info("Индексация успешно остановлена.");
            return ResponseEntity.ok(Map.of("result", true, "message", "Индексация остановлена"));
        } else {
            logger.warn("Индексация не запущена.");
            return ResponseEntity.badRequest().body(Map.of("result", false, "error", "Индексация не запущена"));
        }
    }

    /**
     * Индексация отдельной страницы
     *
     * @param url URL страницы
     * @return Ответ с результатом индексации
     */
    @PostMapping("/indexPage")
    public ResponseEntity<IndexingResponse> indexPage(@RequestParam String url) {

        logger.info("Запрос на индексацию страницы: {}", url);

        // Проверка корректности URL
        if (!isValidUrl(url)) {
            logger.warn("Некорректный URL: {}", url);
            return ResponseEntity.badRequest().body(new IndexingResponse(false, "Некорректный URL"));
        }

        try {
            // Проверка, принадлежит ли страница одному из сайтов из конфигурации
            Site site = findSiteByUrl(url);
            if (site == null) {
                logger.warn("Страница находится за пределами сайтов, указанных в конфигурации: {}", url);
                return ResponseEntity.badRequest().body(new IndexingResponse(false, "Данная страница находится за пределами сайтов, указанных в конфигурации"));
            }

            // Устанавливаем статус сайта в "INDEXING"
            site.setStatus(Status.INDEXING);
            site.setLastError(null);
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);

            // Возвращаем ответ сразу, не дожидаясь завершения индексации
            logger.info("Сайт {} поставлен в очередь на индексацию", site.getUrl());
            return ResponseEntity.ok(new IndexingResponse(true, "Сайт поставлен в очередь на индексацию"));

            // Запуск индексации в отдельном потоке (асинхронно)

        } catch (Exception e) {
            logger.error("Ошибка при постановке сайта в очередь на индексацию: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new IndexingResponse(false, "Ошибка при постановке сайта в очередь на индексацию: " + e.getMessage()));
        }

    }

    /**
     * Проверка корректности URL
     */
    private boolean isValidUrl(String url) {
        try {
            new URL(url);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    /**
     * Поиск сайта по URL страницы
     */
    private Site findSiteByUrl(String url) {
        List<Site> sites = siteRepository.findAll();
        for (Site site : sites) {
            if (url.startsWith(site.getUrl())) {
                return site;
            }
        }
        return null;
    }

    /**
     * Получение пути страницы относительно сайта
     */
    private String getPagePath(String url, Site site) {
        String path = url.replace(site.getUrl(), "");
        return path.isEmpty() ? "/" : path;
    }
}