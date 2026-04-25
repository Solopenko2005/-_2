package searchengine.controller.scopus;

import lombok.extern.slf4j.Slf4j;
import searchengine.model.scopus.ScientificArticle;
import searchengine.services.scopus.ExcelExportService;
import searchengine.services.scopus.TechnologyExtractionService;
import searchengine.services.scopus.WordExportService;
import searchengine.services.scopus.ScopusApiService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Контроллер для работы с научными статьями и экспорта результатов
 * Автоматический поиск статей по темам семеноводства и селекции из Scopus
 */
@Slf4j
@RestController
@RequestMapping("/api/scientific")
public class ScientificArticlesController {

    private final ScopusApiService scopusApiService;
    private final TechnologyExtractionService extractionService;
    private final WordExportService wordExportService;
    private final ExcelExportService excelExportService;

    public ScientificArticlesController(
            ScopusApiService scopusApiService,
            TechnologyExtractionService extractionService,
            WordExportService wordExportService, ExcelExportService excelExportService) {
        this.scopusApiService = scopusApiService;
        this.extractionService = extractionService;
        this.wordExportService = wordExportService;
        this.excelExportService = excelExportService;
    }

    /**
     * Автоматический поиск статей по темам семеноводства и селекции в Scopus
     * Использует предопределённые поисковые запросы из конфигурации
     * @return Список статей с выявленными технологиями, темами, ключевыми словами и аннотациями
     */
    @GetMapping("/scopus/search-auto")
    public ResponseEntity<List<ScientificArticle>> searchSeedBreedingArticles() {
        log.info("Automatically searching Scopus articles for seed breeding topics");

        List<ScientificArticle> articles = scopusApiService.searchSeedBreedingArticles();

        return ResponseEntity.ok(articles);
    }
    /**
     * Экспорт тем и статей в Excel
     * @return Excel файл со статьями по темам
     */
    @GetMapping("/scopus/export-topics-excel")
    public ResponseEntity<byte[]> exportTopicsToExcel() {
        log.info("Exporting topics and articles to Excel");

        // Список тем
        List<String> topics = Arrays.asList(
                "Digital plant breeding",
                "Speed Breeding",
                "Predictive plant breeding",
                "Advanced Genome Editing",
                "Epigenome EditingMulti-Omics and Systems Biology",
                "AI and Machine Learning in Breeding",
                "High-Throughput Phenotyping",
                "Precision Breeding",
                "Synthetic Biology",
                "RNA-Based Technologies (RNAi, SIGS)",
                "Pangenomics and Genetic Diversity",
                "Automation and Robotics in Breeding",
                "Climate-Smart Breeding"
        );

        // Поиск статей по темам
        Map<String, List<ScientificArticle>> topicsWithArticles =
                scopusApiService.searchArticlesByTopics(topics);

        // Создаем Excel файл
        byte[] excelBytes = excelExportService.createTopicsWithArticlesExcel(topicsWithArticles);

        if (excelBytes.length == 0) {
            return ResponseEntity.badRequest().body("Failed to create Excel file".getBytes());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "breeding_technologies_articles.xlsx");
        headers.setContentLength(excelBytes.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(excelBytes);
    }

    /**
     * Поиск статей в Scopus по пользовательскому запросу
     * @param query Поисковый запрос
     * @return Список статей с выявленными технологиями
     */
    @GetMapping("/scopus/search")
    public ResponseEntity<List<ScientificArticle>> searchScopusArticles(
            @RequestParam String query) {

        log.info("Searching Scopus articles with custom query: {}", query);

        List<ScientificArticle> articles = scopusApiService.searchArticles(query);

        return ResponseEntity.ok(articles);
    }
    /**
     * Экспорт тем и статей в Excel с фильтром по годам (2024-2026)
     */
    @GetMapping("/scopus/export-topics-excel-with-years")
    public ResponseEntity<byte[]> exportTopicsToExcelWithYears() throws InterruptedException {
        log.info("Exporting topics and articles to Excel with years 2024-2026");

        List<String> topics = Arrays.asList(
                "Digital plant breeding",
                "Speed Breeding",
                "Predictive plant breeding",
                "Advanced Genome Editing",
                "Epigenome EditingMulti-Omics and Systems Biology",
                "AI and Machine Learning in Breeding",
                "High-Throughput Phenotyping",
                "Precision Breeding",
                "Synthetic Biology",
                "RNA-Based Technologies (RNAi, SIGS)",
                "Pangenomics and Genetic Diversity",
                "Automation and Robotics in Breeding",
                "Climate-Smart Breeding"
        );

        List<Integer> years = Arrays.asList(2024, 2025, 2026);

        // Вариант 1: Все годы в одном запросе
        Map<String, List<ScientificArticle>> topicsWithArticles =
                scopusApiService.searchArticlesByTopicsWithYears(topics, years);

        // Вариант 2: Разбивка по годам (рекомендуется для большей гибкости)
        // Map<String, Map<Integer, List<ScientificArticle>>> topicsData =
        //         scopusApiService.searchArticlesByTopicsAndYearsSeparate(topics, years);
        // byte[] excelBytes = excelExportService.createTopicsWithYearsExcel(topicsData);

        byte[] excelBytes = excelExportService.createTopicsWithArticlesExcel(topicsWithArticles);

        if (excelBytes.length == 0) {
            return ResponseEntity.badRequest().body("Failed to create Excel file".getBytes());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "breeding_technologies_2024_2026.xlsx");
        headers.setContentLength(excelBytes.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(excelBytes);
    }

    /**
     * Экспорт тем и статей с разбивкой по годам (отдельные листы для каждого года)
     */
    @GetMapping("/scopus/export-topics-excel-by-year")
    public ResponseEntity<byte[]> exportTopicsToExcelByYear() throws InterruptedException {
        log.info("Exporting topics and articles to Excel with separate sheets for each year");

        List<String> topics = Arrays.asList(
                "Digital plant breeding",
                "Speed Breeding",
                "Predictive plant breeding",
                "Advanced Genome Editing",
                "AI and Machine Learning in Breeding",
                "High-Throughput Phenotyping",
                "Precision Breeding",
                "Climate-Smart Breeding"
        );

        List<Integer> years = Arrays.asList(2024, 2025, 2026);

        Map<String, Map<Integer, List<ScientificArticle>>> topicsData =
                scopusApiService.searchArticlesByTopicsAndYearsSeparate(topics, years);

        byte[] excelBytes = excelExportService.createTopicsWithYearsExcel(topicsData);

        if (excelBytes.length == 0) {
            return ResponseEntity.badRequest().body("Failed to create Excel file".getBytes());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "breeding_technologies_by_year.xlsx");
        headers.setContentLength(excelBytes.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(excelBytes);
    }

    /**
     * Экспорт результатов анализа в Word файл (Темы + технологии)
     * Автоматический поиск по темам семеноводства и селекции
     * @return Word документ
     */
    @GetMapping("/scopus/export-topics-auto")
    public ResponseEntity<byte[]> exportTopicsFileAuto() {
        log.info("Exporting topics file for seed breeding topics automatically");

        List<ScientificArticle> articles = scopusApiService.searchSeedBreedingArticles();

        if (articles.isEmpty()) {
            return ResponseEntity.badRequest().body("No articles found".getBytes());
        }

        byte[] documentBytes = wordExportService.createTechnologiesAndTopicsFile(articles);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "topics_technologies.docx");
        headers.setContentLength(documentBytes.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(documentBytes);
    }

    /**
     * Экспорт результатов анализа в Word файл (Аннотации + ключевые слова + технологии)
     * Автоматический поиск по темам семеноводства и селекции
     * @return Word документ
     */
    @GetMapping("/scopus/export-abstracts-auto")
    public ResponseEntity<byte[]> exportAbstractsFileAuto() {
        log.info("Exporting abstracts file for seed breeding topics automatically");

        List<ScientificArticle> articles = scopusApiService.searchSeedBreedingArticles();

        if (articles.isEmpty()) {
            return ResponseEntity.badRequest().body("No articles found".getBytes());
        }

        byte[] documentBytes = wordExportService.createAbstractsKeywordsFile(articles);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "abstracts_keywords_technologies.docx");
        headers.setContentLength(documentBytes.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(documentBytes);
    }

    /**
     * Экспорт результатов анализа в Word файл (Темы + технологии)
     * @param query Поисковый запрос для получения статей
     * @return Word документ
     */
    @GetMapping("/scopus/export-topics")
    public ResponseEntity<byte[]> exportTopicsFile(
            @RequestParam String query) {

        log.info("Exporting topics file for query: {}", query);

        List<ScientificArticle> articles = scopusApiService.searchArticles(query);

        if (articles.isEmpty()) {
            return ResponseEntity.badRequest().body("No articles found".getBytes());
        }

        byte[] documentBytes = wordExportService.createTechnologiesAndTopicsFile(articles);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "topics_technologies.docx");
        headers.setContentLength(documentBytes.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(documentBytes);
    }

    /**
     * Экспорт результатов анализа в Word файл (Аннотации + ключевые слова + технологии)
     * @param query Поисковый запрос для получения статей
     * @return Word документ
     */
    @GetMapping("/scopus/export-abstracts")
    public ResponseEntity<byte[]> exportAbstractsFile(
            @RequestParam String query) {

        log.info("Exporting abstracts file for query: {}", query);

        List<ScientificArticle> articles = scopusApiService.searchArticles(query);

        if (articles.isEmpty()) {
            return ResponseEntity.badRequest().body("No articles found".getBytes());
        }

        byte[] documentBytes = wordExportService.createAbstractsKeywordsFile(articles);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "abstracts_keywords_technologies.docx");
        headers.setContentLength(documentBytes.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(documentBytes);
    }

    /**
     * Тестирование извлечения технологий на тестовых данных
     */
    @PostMapping("/test-extraction")
    public ResponseEntity<?> testTechnologyExtraction(
            @RequestBody TestArticleRequest request) {

        log.info("Testing technology extraction");

        var result = extractionService.extractTechnologies(
                request.getTitle(),
                request.getAbstractText(),
                request.getKeywords(),
                request.getLanguage()
        );

        return ResponseEntity.ok(result);
    }

    /**
     * DTO для тестового запроса
     */
    public static class TestArticleRequest {
        private String title;
        private String abstractText;
        private List<String> keywords;
        private String language = "en";

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getAbstractText() { return abstractText; }
        public void setAbstractText(String abstractText) { this.abstractText = abstractText; }
        public List<String> getKeywords() { return keywords; }
        public void setKeywords(List<String> keywords) { this.keywords = keywords; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
    }

}