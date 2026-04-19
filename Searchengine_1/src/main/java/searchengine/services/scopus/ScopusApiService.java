package searchengine.services.scopus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import searchengine.config.scopus.ScientificSourcesConfig;
import searchengine.model.scopus.ScientificArticle;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для работы с Scopus API
 * Реализует поиск научных статей с фокусом на поиск по названию
 */
@Slf4j
@Service
public class ScopusApiService {

    private final ScientificSourcesConfig config;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final TechnologyExtractionService extractionService;

    // Параметры поиска по умолчанию согласно документации Scopus API
    private static final int DEFAULT_COUNT = 25;
    private static final int MAX_COUNT = 200;
    private static final String DEFAULT_VIEW = "COMPLETE";
    private static final String SORT_BY_RELEVANCE = "relevance";
    private static final String SORT_BY_DATE_DESC = "-coverDate";

    public ScopusApiService(
            ScientificSourcesConfig config,
            TechnologyExtractionService extractionService) {
        this.config = config;
        this.extractionService = extractionService;

        String apiKey = config.getScopus().getApiKey();
        log.info("Initializing Scopus WebClient with base URL: {}",
                config.getScopus().getBaseUrl());

        this.webClient = WebClient.builder()
                .baseUrl(config.getScopus().getBaseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    // ======================== ПОИСК ПО НАЗВАНИЮ СТАТЬИ ========================

    /**
     * Поиск статей по точному названию (exact title match)
     * Использует поле TITLE() в Scopus API
     *
     * @param exactTitle Точное название статьи
     * @return Список найденных статей
     */
    public List<ScientificArticle> searchByExactTitle(String exactTitle) {
        log.info("Searching Scopus by exact title: {}", exactTitle);

        if (exactTitle == null || exactTitle.isBlank()) {
            log.warn("Empty title provided for search");
            return Collections.emptyList();
        }

        // Экранирование кавычек и специальных символов
        String escapedTitle = escapeSearchValue(exactTitle);
        String query = String.format("TITLE(\"%s\")", escapedTitle);

        return executeSearch(query);
    }

    /**
     * Поиск статей по части названия (partial title match)
     * Использует поле TITLE() с оператором CONTAINS или без кавычек
     *
     * @param titlePart Часть названия статьи
     * @return Список найденных статей
     */
    public List<ScientificArticle> searchByTitlePartial(String titlePart) {
        log.info("Searching Scopus by partial title: {}", titlePart);

        if (titlePart == null || titlePart.isBlank()) {
            log.warn("Empty title part provided for search");
            return Collections.emptyList();
        }

        String escapedTitle = escapeSearchValue(titlePart);
        // Поиск по части названия - без кавычек для частичного совпадения
        String query = String.format("TITLE(%s)", escapedTitle);

        return executeSearch(query);
    }

    /**
     * Поиск статей по ключевым словам в названии
     * Использует логические операторы для комбинации ключевых слов
     *
     * @param keywords Список ключевых слов для поиска в названии
     * @param useAndOperator true - все ключевые слова должны присутствовать (AND),
     *                       false - достаточно любого из ключевых слов (OR)
     * @return Список найденных статей
     */
    public List<ScientificArticle> searchByTitleKeywords(List<String> keywords, boolean useAndOperator) {
        log.info("Searching Scopus by title keywords: {}, operator: {}", keywords, useAndOperator ? "AND" : "OR");

        if (keywords == null || keywords.isEmpty()) {
            log.warn("Empty keywords list provided for search");
            return Collections.emptyList();
        }

        String operator = useAndOperator ? " AND " : " OR ";
        String keywordQuery = keywords.stream()
                .map(k -> String.format("TITLE(%s)", escapeSearchValue(k)))
                .collect(Collectors.joining(operator));

        return executeSearch(keywordQuery);
    }

    /**
     * Поиск статей по названию с дополнительными фильтрами
     *
     * @param titleQuery Поисковый запрос по названию
     * @param year Год публикации (опционально)
     * @param author Фамилия автора (опционально)
     * @param subjectArea Код предметной области (опционально, например "AGRI", "BIOC")
     * @return Список найденных статей
     */
    public List<ScientificArticle> searchByTitleWithFilters(
            String titleQuery,
            Integer year,
            String author,
            String subjectArea) {

        log.info("Searching Scopus by title with filters - title: {}, year: {}, author: {}, subject: {}",
                titleQuery, year, author, subjectArea);

        List<String> queryParts = new ArrayList<>();

        // Добавляем поиск по названию
        if (titleQuery != null && !titleQuery.isBlank()) {
            queryParts.add(String.format("TITLE(%s)", escapeSearchValue(titleQuery)));
        }

        // Добавляем фильтр по году
        if (year != null && year > 0) {
            queryParts.add(String.format("PUBYEAR = %d", year));
        }

        // Добавляем фильтр по автору
        if (author != null && !author.isBlank()) {
            queryParts.add(String.format("AUTHLASTNAME(%s)", escapeSearchValue(author)));
        }

        // Добавляем фильтр по предметной области
        if (subjectArea != null && !subjectArea.isBlank()) {
            queryParts.add(String.format("SUBJAREA(%s)", subjectArea.toUpperCase()));
        }

        if (queryParts.isEmpty()) {
            log.warn("No search criteria provided");
            return Collections.emptyList();
        }

        String fullQuery = String.join(" AND ", queryParts);
        return executeSearch(fullQuery);
    }

    /**
     * Поиск статей с использованием полнотекстового поиска Scopus
     * Поля для поиска: title, abstract, keywords
     *
     * @param searchText Текст для поиска
     * @return Список найденных статей
     */
    public List<ScientificArticle> fullTextSearch(String searchText) {
        log.info("Full text search in Scopus: {}", searchText);

        if (searchText == null || searchText.isBlank()) {
            log.warn("Empty search text provided");
            return Collections.emptyList();
        }

        String escapedText = escapeSearchValue(searchText);
        // TITLE-ABS-KEY ищет по названию, аннотации и ключевым словам
        String query = String.format("TITLE-ABS-KEY(%s)", escapedText);

        return executeSearch(query);
    }

    // ======================== ПОИСК ПО ДРУГИМ ИДЕНТИФИКАТОРАМ ========================

    /**
     * Поиск статьи по DOI
     *
     * @param doi DOI статьи
     * @return Найденная статья или null
     */
    public ScientificArticle searchByDoi(String doi) {
        log.info("Searching Scopus by DOI: {}", doi);

        if (doi == null || doi.isBlank()) {
            log.warn("Empty DOI provided");
            return null;
        }

        String query = String.format("DOI(%s)", escapeSearchValue(doi));
        List<ScientificArticle> results = executeSearch(query);

        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Поиск статей по ISSN журнала
     *
     * @param issn ISSN журнала (с дефисом или без)
     * @param year Год публикации (опционально)
     * @return Список найденных статей
     */
    public List<ScientificArticle> searchByIssn(String issn, Integer year) {
        log.info("Searching Scopus by ISSN: {}, year: {}", issn, year);

        if (issn == null || issn.isBlank()) {
            log.warn("Empty ISSN provided");
            return Collections.emptyList();
        }

        // Нормализация ISSN (удаление дефиса)
        String normalizedIssn = issn.replace("-", "");
        String query = String.format("ISSN(%s)", normalizedIssn);

        if (year != null && year > 0) {
            query += String.format(" AND PUBYEAR = %d", year);
        }

        return executeSearch(query);
    }

    /**
     * Поиск статей по ID автора Scopus
     *
     * @param authorId Scopus Author ID
     * @return Список статей автора
     */
    public List<ScientificArticle> searchByAuthorId(String authorId) {
        log.info("Searching Scopus by Author ID: {}", authorId);

        if (authorId == null || authorId.isBlank()) {
            log.warn("Empty Author ID provided");
            return Collections.emptyList();
        }

        String query = String.format("AU-ID(%s)", authorId);

        return executeSearch(query);
    }

    /**
     * Поиск статей по Affiliation ID
     *
     * @param affiliationId Scopus Affiliation ID
     * @return Список статей организации
     */
    public List<ScientificArticle> searchByAffiliationId(String affiliationId) {
        log.info("Searching Scopus by Affiliation ID: {}", affiliationId);

        if (affiliationId == null || affiliationId.isBlank()) {
            log.warn("Empty Affiliation ID provided");
            return Collections.emptyList();
        }

        String query = String.format("AF-ID(%s)", affiliationId);

        return executeSearch(query);
    }

    // ======================== АВТОМАТИЧЕСКИЙ ПОИСК ПО ТЕМАМ ========================

    /**
     * Автоматический поиск статей по темам семеноводства и селекции
     * Использует поиск по названиям статей
     *
     * @return Список найденных статей
     */
    public List<ScientificArticle> searchSeedBreedingArticles() {
        log.info("Automatically searching Scopus articles for seed breeding topics by title");

        List<String> titleQueries = getSeedBreedingTitleQueries();

        if (titleQueries == null || titleQueries.isEmpty()) {
            titleQueries = getDefaultTitleQueries();
        }

        List<ScientificArticle> allArticles = new ArrayList<>();

        for (String query : titleQueries) {
            log.info("Executing title search query: {}", query);
            // Используем поиск по названию с оберткой TITLE()
            String scopusQuery = String.format("TITLE(%s)", query);
            List<ScientificArticle> articles = executeSearch(scopusQuery);
            allArticles.addAll(articles);
        }

        // Удаляем дубликаты по DOI или sourceId
        return allArticles.stream()
                .filter(article -> article.getDoi() != null && !article.getDoi().isEmpty())
                .collect(Collectors.toMap(
                        article -> article.getDoi() != null ? article.getDoi() : article.getSourceId(),
                        article -> article,
                        (existing, replacement) -> existing
                ))
                .values()
                .stream()
                .collect(Collectors.toList());
    }

    // ======================== БАЗОВЫЙ МЕТОД ПОИСКА ========================

    /**
     * Базовый метод выполнения поискового запроса к Scopus API
     * Полностью соответствует документации Scopus API
     *
     * @param query Поисковый запрос в формате Scopus
     * @return Список найденных статей
     */
    public List<ScientificArticle> searchArticles(String query) {
        return executeSearch(query);
    }

    /**
     * Расширенный поиск с полным контролем параметров
     *
     * @param query Поисковый запрос
     * @param start Начальная позиция (смещение)
     * @param count Количество результатов (макс 200)
     * @param view Режим просмотра (STANDARD, COMPLETE)
     * @param sort Поле сортировки
     * @param facets Фасеты для агрегации
     * @return Список статей
     */
    public List<ScientificArticle> searchArticlesAdvanced(
            String query,
            Integer start,
            Integer count,
            String view,
            String sort,
            String facets) {

        log.info("Advanced Scopus search - query: {}, start: {}, count: {}, view: {}, sort: {}, facets: {}",
                query, start, count, view, sort, facets);

        String apiKey = config.getScopus().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.error("Scopus API key is not configured");
            return Collections.emptyList();
        }

        try {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder
                    .fromPath("/content/search/scopus")
                    .queryParam("query", query)
                    .queryParam("start", start != null ? start : 0)
                    .queryParam("count", Math.min(count != null ? count : DEFAULT_COUNT, MAX_COUNT))
                    .queryParam("view", view != null ? view : DEFAULT_VIEW);

            if (sort != null && !sort.isBlank()) {
                uriBuilder.queryParam("sort", sort);
            }

            if (facets != null && !facets.isBlank()) {
                uriBuilder.queryParam("facets", facets);
            }

            String response = webClient.get()
                    .uri(uriBuilder.build().toUri())
                    .header("X-ELS-APIKey", apiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseScopusResponse(response);

        } catch (Exception e) {
            log.error("Error during advanced Scopus search: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Выполнение поискового запроса к Scopus API
     *
     * @param query Поисковый запрос в формате Scopus
     * @return Список найденных статей
     */
    private List<ScientificArticle> executeSearch(String query) {
        log.info("Executing Scopus search with query: {}", query);

        String apiKey = config.getScopus().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.error("Scopus API key is not configured");
            return Collections.emptyList();
        }

        int maxResults = config.getScopus().getMaxResults();
        maxResults = Math.min(maxResults > 0 ? maxResults : DEFAULT_COUNT, MAX_COUNT);

        try {
            int finalMaxResults = maxResults;
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/content/search/scopus")
                            .queryParam("query", query)
                            .queryParam("count", finalMaxResults)
                            .queryParam("start", 0)
                            .queryParam("view", DEFAULT_VIEW)
                            .queryParam("sort", SORT_BY_RELEVANCE)
                            .build())
                    .header("X-ELS-APIKey", apiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseScopusResponse(response);

        } catch (Exception e) {
            log.error("Error executing Scopus search: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    // ======================== ПАРСИНГ ОТВЕТА ========================

    /**
     * Парсинг ответа от Scopus API в соответствии с документацией
     */
    private List<ScientificArticle> parseScopusResponse(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode searchResults = root.path("search-results");

            // Проверка на ошибки
            if (searchResults.has("error")) {
                log.error("Scopus API returned error: {}", searchResults.path("error").asText());
                return Collections.emptyList();
            }

            JsonNode entries = searchResults.path("entry");
            int totalResults = searchResults.path("opensearch:totalResults").asInt(0);

            log.info("Scopus API returned {} total results, processing {} entries",
                    totalResults, entries.size());

            List<ScientificArticle> articles = new ArrayList<>();

            if (entries.isArray()) {
                for (JsonNode entry : entries) {
                    ScientificArticle article = parseArticleEntry(entry);
                    if (article != null) {
                        articles.add(article);
                    }
                }
            }

            log.info("Successfully parsed {} articles from Scopus", articles.size());
            return articles;

        } catch (Exception e) {
            log.error("Error parsing Scopus response: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Парсинг одной записи статьи из ответа Scopus
     */
    private ScientificArticle parseArticleEntry(JsonNode entry) {
        try {
            // Извлечение основных полей в соответствии с документацией Scopus
            String sourceId = entry.path("dc:identifier").asText("");
            String doi = entry.path("prism:doi").asText("");
            String title = entry.path("dc:title").asText("");
            String abstractText = entry.path("dc:description").asText("");
            String language = entry.path("language").asText("");
            String publicationName = entry.path("prism:publicationName").asText("");
            String publisher = entry.path("dc:publisher").asText("");
            String link = entry.path("link").path("href").asText("");

            // Извлечение года публикации
            String coverDate = entry.path("prism:coverDate").asText("");
            Integer publicationYear = extractPublicationYear(coverDate);

            // Извлечение цитирований
            Integer citedByCount = entry.path("citedby-count").asInt(0);

            // Извлечение авторов
            List<String> authors = extractAuthors(entry);

            // Извлечение ключевых слов
            List<String> keywords = extractKeywords(entry);

            // Извлечение типов документа
            String documentType = entry.path("subtypeDescription").asText("");

            // Извлечение информации о открытом доступе
            String openAccess = entry.path("openaccess").asText("");

            // Создание объекта статьи
            ScientificArticle article = ScientificArticle.builder()
                    .sourceId(sourceId)
                    .sourceName("SCOPUS")
                    .title(title)
                    .abstractText(abstractText)
                    .keywords(keywords)
                    .language(language != null && !language.isEmpty() ? language : "en")
                    .publicationYear(publicationYear)
                    .authors(authors)
                    .doi(doi)
                    .publicationName(publicationName)
                    .publisher(publisher)
                    .citedByCount(citedByCount)
                    .documentType(documentType)
                    .openAccess(openAccess)
                    .link(link)
                    .build();

            // Извлечение технологий из статьи (если сервис доступен)
            if (extractionService != null) {
                try {
                    var extractionResult = extractionService.extractTechnologies(
                            article.getTitle(),
                            article.getAbstractText(),
                            article.getKeywords(),
                            article.getLanguage()
                    );
                    article.setExtractedTechnologies(extractionResult.getTechnologyFrequency());
                } catch (Exception e) {
                    log.debug("Could not extract technologies for article: {}", title, e);
                }
            }

            return article;

        } catch (Exception e) {
            log.error("Error parsing article entry: {}", e.getMessage());
            return null;
        }
    }

    // ======================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ========================

    /**
     * Экранирование специальных символов для поискового запроса Scopus
     */
    private String escapeSearchValue(String value) {
        if (value == null) return "";
        // Экранирование двойных кавычек и других специальных символов
        return value.replace("\"", "\\\"")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace(":", "\\:")
                .replace("/", "\\/");
    }

    /**
     * Извлечение года публикации из даты
     */
    private Integer extractPublicationYear(String coverDate) {
        if (coverDate == null || coverDate.isEmpty()) return null;
        try {
            // Поддержка разных форматов даты
            if (coverDate.length() >= 4) {
                return Integer.parseInt(coverDate.substring(0, 4));
            }
        } catch (NumberFormatException e) {
            log.debug("Could not parse year from: {}", coverDate);
        }
        return null;
    }

    /**
     * Извлечение списка авторов из ответа Scopus
     */
    private List<String> extractAuthors(JsonNode entry) {
        List<String> authors = new ArrayList<>();
        JsonNode authorArray = entry.path("author");
        if (authorArray.isArray()) {
            for (JsonNode author : authorArray) {
                String givenName = author.path("given-name").asText("");
                String surname = author.path("surname").asText("");
                String authorName = (givenName + " " + surname).trim();
                if (!authorName.isEmpty()) {
                    authors.add(authorName);
                }
            }
        }
        return authors;
    }

    /**
     * Извлечение ключевых слов из ответа Scopus
     */
    private List<String> extractKeywords(JsonNode entry) {
        List<String> keywords = new ArrayList<>();

        // Поле authkeywords содержит ключевые слова, разделенные |
        String authKeywords = entry.path("authkeywords").asText("");
        if (!authKeywords.isEmpty()) {
            String[] parts = authKeywords.split("\\|");
            for (String part : parts) {
                String keyword = part.trim();
                if (!keyword.isEmpty()) {
                    keywords.add(keyword);
                }
            }
        }

        return keywords;
    }

    /**
     * Получение поисковых запросов для семеноводства и селекции
     * Оптимизировано для поиска по названиям статей
     */
    private List<String> getSeedBreedingTitleQueries() {
        List<String> queries = config.getSeedBreedingTopics().getSearchQueriesEn();
        if (queries != null && !queries.isEmpty()) {
            // Трансформируем запросы для поиска по названию
            return queries.stream()
                    .map(q -> q.replace(" AND ", " AND TITLE(")
                            .replace(" OR ", " OR TITLE("))
                    .collect(Collectors.toList());
        }
        return null;
    }

    /**
     * Поисковые запросы по умолчанию для поиска в названиях статей
     */
    private List<String> getDefaultTitleQueries() {
        return List.of(
                "seed breeding",
                "plant breeding",
                "crop improvement",
                "seed production technology",
                "hybrid seed",
                "molecular breeding",
                "seed quality",
                "seed germination",
                "seed treatment",
                "genetic improvement of seeds"
        );
    }
}