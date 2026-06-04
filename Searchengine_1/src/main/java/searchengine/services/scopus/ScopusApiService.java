package searchengine.services.scopus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import searchengine.config.scopus.ScientificSourcesConfig;
import searchengine.model.scopus.ScientificArticle;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ScopusApiService {

    private final ScientificSourcesConfig config;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final TechnologyExtractionService extractionService;

    private static final int DEFAULT_COUNT = 25;
    private static final String SORT_BY_RELEVANCE = "relevance";
    private static final String SCOPUS_SEARCH_ENDPOINT = "/content/search/scopus";

    public ScopusApiService(ScientificSourcesConfig config, TechnologyExtractionService extractionService) {
        this.config = config;
        this.extractionService = extractionService;
        this.webClient = WebClient.builder()
                .baseUrl(config.getScopus().getBaseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Базовый метод выполнения поискового запроса к Scopus API
     */
    private List<ScientificArticle> executeSearch(String query) {
        log.info("Executing Scopus search with query: {}", query);

        String apiKey = config.getScopus().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.error("Scopus API key is not configured");
            return Collections.emptyList();
        }

        try {
            String responseBody = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(SCOPUS_SEARCH_ENDPOINT)
                            .queryParam("query", query)
                            .queryParam("count", DEFAULT_COUNT)
                            .queryParam("start", 0)
                            .queryParam("sort", SORT_BY_RELEVANCE)
                            .queryParam("apiKey", apiKey)
                            .build())
                    .header("X-ELS-APIKey", apiKey)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .doOnNext(body -> log.error("Error body: {}", body))
                                    .then(Mono.error(new RuntimeException("Scopus API error: " + clientResponse.statusCode().value()))))
                    .bodyToMono(String.class)
                    .block();

            if (responseBody == null) {
                log.error("Received null response from Scopus API");
                return Collections.emptyList();
            }

            return parseScopusResponse(responseBody);

        } catch (Exception e) {
            log.error("Error executing Scopus search: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private boolean isArticleRelevantToTopic(ScientificArticle article, String topic) {
        String text = String.join(" ",
                Optional.ofNullable(article.getTitle()).orElse(""),
                Optional.ofNullable(article.getAbstractText()).orElse(""),
                article.getKeywords() != null ? String.join(" ", article.getKeywords()) : ""
        ).toLowerCase();

        String normalizedTopic = topic.toLowerCase();

        List<String> excluded = List.of(
                "human", "patient", "clinical", "hospital", "medical", "surgery",
                "finance", "banking", "stock market",
                "poultry", "livestock", "animal breeding", "cattle", "pig", "chicken"
        );

        if (excluded.stream().anyMatch(text::contains)) {
            return false;
        }

        boolean hasBreedingContext =
                text.contains("plant breeding")
                        || text.contains("crop breeding")
                        || text.contains("breeding program")
                        || text.contains("crop improvement")
                        || text.contains("genomic selection")
                        || text.contains("marker-assisted selection")
                        || text.contains("cultivar")
                        || text.contains("germplasm");

        if (!hasBreedingContext) {
            return false;
        }

        if (normalizedTopic.contains("digital plant breeding")) {
            return text.contains("digital plant breeding")
                    || text.contains("digital agriculture")
                    || text.contains("precision agriculture")
                    || text.contains("breeding program");
        }

        if (normalizedTopic.contains("speed breeding")) {
            return text.contains("speed breeding")
                    || text.contains("rapid generation advancement");
        }

        if (normalizedTopic.contains("predictive plant breeding")) {
            return text.contains("genomic prediction")
                    || text.contains("genomic selection")
                    || text.contains("predictive breeding");
        }

        if (normalizedTopic.contains("genome editing")) {
            return text.contains("crispr")
                    || text.contains("genome editing")
                    || text.contains("gene editing");
        }

        if (normalizedTopic.contains("machine learning")
                || normalizedTopic.contains("artificial intelligence")) {
            return text.contains("machine learning")
                    || text.contains("deep learning")
                    || text.contains("artificial intelligence")
                    || text.contains("genomic prediction");
        }

        if (normalizedTopic.contains("high-throughput phenotyping")) {
            return text.contains("high-throughput phenotyping")
                    || text.contains("plant phenotyping")
                    || text.contains("phenomics");
        }

        if (normalizedTopic.contains("precision breeding")) {
            return text.contains("precision breeding")
                    || text.contains("marker-assisted selection")
                    || text.contains("molecular marker")
                    || text.contains("genomic selection");
        }

        if (normalizedTopic.contains("climate-smart")) {
            return text.contains("climate-smart breeding")
                    || text.contains("drought tolerance")
                    || text.contains("heat tolerance")
                    || text.contains("climate resilience");
        }

        return true;
    }

    /**
     * Поиск статей для списка тем с фильтрацией по годам
     */
    public Map<String, List<ScientificArticle>> searchArticlesByTopicsWithYears(
            List<String> topics, List<Integer> years) throws InterruptedException {

        log.info("Searching Scopus articles for {} topics with years: {}", topics.size(), years);

        Map<String, List<ScientificArticle>> result = new LinkedHashMap<>();

        for (int i = 0; i < topics.size(); i++) {
            String topic = topics.get(i);
            log.info("Searching Scopus for topic: {} ({}/{})", topic, i + 1, topics.size());

            List<ScientificArticle> allArticles = new ArrayList<>();

            for (Integer year : years) {
                String searchTerm = getScopusSearchTerm(topic);
                String query = String.format(
                        "TITLE-ABS-KEY(%s AND %s) AND PUBYEAR = %d AND DOCTYPE(ar) AND SRCTYPE(j) AND LANGUAGE(english)",
                        searchTerm, PLANT_BREEDING_CONTEXT, year
                );

                List<ScientificArticle> articles = executeSearch(query);

                List<ScientificArticle> filtered = articles.stream()
                        .filter(a -> a.getPublicationYear() != null && a.getPublicationYear().equals(year))
                        .filter(a -> isArticleRelevantToTopic(a, topic))
                        .limit(5)
                        .collect(Collectors.toList());

                allArticles.addAll(filtered);
                log.info("Found {} Scopus articles for {} in {}", filtered.size(), topic, year);

                Thread.sleep(1500);
            }

            result.put(topic, allArticles);
        }

        return result;
    }

    /**
     * Поиск статей для тем с разбивкой по годам (отдельные запросы для каждого года)
     * Возвращает Map: тема -> год -> список статей
     */
    public Map<String, Map<Integer, List<ScientificArticle>>> searchArticlesByTopicsAndYearsSeparate(
            List<String> topics, List<Integer> years) throws InterruptedException {

        log.info("Searching Scopus articles for {} topics with separate years: {}", topics.size(), years);

        Map<String, Map<Integer, List<ScientificArticle>>> result = new LinkedHashMap<>();

        for (String topic : topics) {
            log.info("Processing topic: {}", topic);
            Map<Integer, List<ScientificArticle>> yearArticles = new LinkedHashMap<>();

            for (Integer year : years) {
                log.info("  Searching Scopus for year: {}", year);

                String searchTerm = getScopusSearchTerm(topic);
                String query = String.format(
                        "TITLE-ABS-KEY(%s AND %s) AND PUBYEAR = %d AND DOCTYPE(ar) AND SRCTYPE(j) AND LANGUAGE(english)",
                        searchTerm, PLANT_BREEDING_CONTEXT, year
                );

                List<ScientificArticle> articles = executeSearch(query);

                List<ScientificArticle> filtered = articles.stream()
                        .filter(a -> a.getPublicationYear() != null && a.getPublicationYear().equals(year))
                        .filter(a -> isArticleRelevantToTopic(a, topic))
                        .limit(5)
                        .collect(Collectors.toList());

                yearArticles.put(year, filtered);
                log.info("    Found {} Scopus articles for {} in {}", filtered.size(), topic, year);

                Thread.sleep(1500);
            }

            result.put(topic, yearArticles);
        }

        return result;
    }

    /**
     * Поиск статей для списка тем (без фильтра по годам)
     */
    public Map<String, List<ScientificArticle>> searchArticlesByTopics(List<String> topics) {
        log.info("Searching Scopus articles for {} topics", topics.size());

        Map<String, List<ScientificArticle>> result = new LinkedHashMap<>();

        for (String topic : topics) {
            log.info("Searching Scopus for topic: {}", topic);

            String searchTerm = getScopusSearchTerm(topic);
            String query = String.format(
                    "TITLE-ABS-KEY(%s AND %s) AND DOCTYPE(ar) AND SRCTYPE(j) AND LANGUAGE(english)",
                    searchTerm, PLANT_BREEDING_CONTEXT
            );
            List<ScientificArticle> articles = executeSearch(query);
            List<ScientificArticle> limited = articles.stream()
                    .filter(a -> isArticleRelevantToTopic(a, topic))
                    .limit(5)
                    .collect(Collectors.toList());

            result.put(topic, limited);
            log.info("Found {} Scopus articles for topic: {}", limited.size(), topic);
        }

        return result;
    }

    /**
     * Возвращает поисковый термин для Scopus
     */
    private String getScopusSearchTerm(String topic) {
        String normalized = topic.toLowerCase();

        if (normalized.contains("digital plant breeding")) {
            return "(" +
                    "\"digital plant breeding\"" +
                    " OR " +
                    "(\"digital agriculture\" AND \"plant breeding\")" +
                    " OR " +
                    "(\"precision agriculture\" AND \"breeding program\")" +
                    ")";
        }

        if (normalized.contains("speed breeding")) {
            return "(" +
                    "\"speed breeding\"" +
                    " OR " +
                    "\"rapid generation advancement\"" +
                    ")";
        }

        if (normalized.contains("predictive plant breeding")) {
            return "(" +
                    "\"genomic prediction\"" +
                    " OR " +
                    "\"genomic selection\"" +
                    " OR " +
                    "(\"predictive breeding\" AND plant)" +
                    ")";
        }

        if (normalized.contains("genome editing")) {
            return "(" +
                    "(CRISPR AND plant)" +
                    " OR " +
                    "(\"genome editing\" AND plant)" +
                    " OR " +
                    "(\"gene editing\" AND crop)" +
                    ")";
        }

        if (normalized.contains("epigenome") || normalized.contains("multi-omics")) {
            return "(" +
                    "(epigenomics AND plant)" +
                    " OR " +
                    "(\"multi-omics\" AND breeding)" +
                    " OR " +
                    "(transcriptomics AND breeding)" +
                    " OR " +
                    "(metabolomics AND breeding)" +
                    ")";
        }

        if (normalized.contains("machine learning")
                || normalized.contains("artificial intelligence")) {

            return "(" +
                    "(\"machine learning\" AND \"plant breeding\")" +
                    " OR " +
                    "(\"deep learning\" AND \"plant breeding\")" +
                    " OR " +
                    "(\"artificial intelligence\" AND \"plant breeding\")" +
                    " OR " +
                    "(\"genomic prediction\" AND breeding)" +
                    ")";
        }

        if (normalized.contains("high-throughput phenotyping")) {
            return "(" +
                    "\"high-throughput phenotyping\"" +
                    " OR " +
                    "(\"plant phenotyping\" AND breeding)" +
                    " OR " +
                    "(phenomics AND breeding)" +
                    ")";
        }

        if (normalized.contains("precision breeding")) {
            return "(" +
                    "\"marker-assisted selection\"" +
                    " OR " +
                    "\"genomic selection\"" +
                    " OR " +
                    "\"molecular marker\"" +
                    " OR " +
                    "(\"precision breeding\" AND plant)" +
                    ")";
        }

        if (normalized.contains("synthetic biology")) {
            return "(" +
                    "(\"synthetic biology\" AND plant)" +
                    " OR " +
                    "(\"synthetic biology\" AND crop)" +
                    ")";
        }

        if (normalized.contains("rna-based")
                || normalized.contains("rnai")) {

            return "(" +
                    "(RNAi AND plant)" +
                    " OR " +
                    "(\"RNA interference\" AND crop)" +
                    " OR " +
                    "(SIGS AND plant)" +
                    ")";
        }

        if (normalized.contains("pangenomics")) {
            return "(" +
                    "(pangenome AND crop)" +
                    " OR " +
                    "(pangenomics AND breeding)" +
                    " OR " +
                    "(\"genetic diversity\" AND breeding)" +
                    ")";
        }

        if (normalized.contains("automation")
                || normalized.contains("robotics")) {

            return "(" +
                    "(robotics AND breeding)" +
                    " OR " +
                    "(\"field robot\" AND crop)" +
                    " OR " +
                    "(\"automated phenotyping\" AND breeding)" +
                    ")";
        }

        if (normalized.contains("climate-smart")) {
            return "(" +
                    "(\"climate-smart breeding\")" +
                    " OR " +
                    "(\"drought tolerance\" AND breeding)" +
                    " OR " +
                    "(\"heat tolerance\" AND breeding)" +
                    " OR " +
                    "(\"climate resilience\" AND crop)" +
                    ")";
        }

        return "\"" + topic + "\"";
    }

    /**
     * Парсинг ответа Scopus API
     */
    private List<ScientificArticle> parseScopusResponse(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode searchResults = root.path("search-results");

            if (searchResults.has("error")) {
                log.error("Scopus API returned error: {}", searchResults.path("error").asText());
                return Collections.emptyList();
            }

            JsonNode entries = searchResults.path("entry");
            int totalResults = searchResults.path("opensearch:totalResults").asInt(0);

            log.info("Scopus API returned {} total results from Scopus database", totalResults);

            List<ScientificArticle> articles = new ArrayList<>();

            if (entries.isArray()) {
                for (JsonNode entry : entries) {
                    ScientificArticle article = parseArticleEntry(entry);
                    if (article != null) {
                        articles.add(article);
                    }
                }
            }

            log.info("Successfully parsed {} Scopus articles", articles.size());
            return articles;

        } catch (Exception e) {
            log.error("Error parsing Scopus response: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Парсинг одной записи статьи
     */
    private ScientificArticle parseArticleEntry(JsonNode entry) {
        try {
            String sourceId = entry.path("dc:identifier").asText("");
            String doi = entry.path("prism:doi").asText("");
            String title = entry.path("dc:title").asText("");
            String abstractText = entry.path("dc:description").asText("");
            String language = entry.path("language").asText("");
            String publicationName = entry.path("prism:publicationName").asText("");
            String publisher = entry.path("dc:publisher").asText("");

            String coverDate = entry.path("prism:coverDate").asText("");
            Integer publicationYear = extractPublicationYear(coverDate);

            Integer citedByCount = entry.path("citedby-count").asInt(0);
            List<String> authors = extractAuthors(entry);
            List<String> keywords = extractKeywords(entry);

            String documentType = entry.path("subtypeDescription").asText("");
            String openAccess = entry.path("openaccess").asText("");

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
                    .build();

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

    private Integer extractPublicationYear(String coverDate) {
        if (coverDate == null || coverDate.isEmpty()) return null;
        try {
            if (coverDate.length() >= 4) {
                return Integer.parseInt(coverDate.substring(0, 4));
            }
        } catch (NumberFormatException e) {
            log.debug("Could not parse year from: {}", coverDate);
        }
        return null;
    }

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

    private List<String> extractKeywords(JsonNode entry) {
        List<String> keywords = new ArrayList<>();
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

    public List<ScientificArticle> searchArticles(String query) {
        return executeSearch(query);
    }

    public List<ScientificArticle> searchSeedBreedingArticles() {
        List<String> queries = Arrays.asList(
                "TITLE-ABS-KEY(\"seed breeding\") AND DOCTYPE(ar) AND SRCTYPE(j)",
                "TITLE-ABS-KEY(\"plant breeding\") AND DOCTYPE(ar) AND SRCTYPE(j)"
        );

        List<ScientificArticle> allArticles = new ArrayList<>();
        for (String query : queries) {
            allArticles.addAll(executeSearch(query));
        }

        return allArticles.stream()
                .filter(article -> article.getDoi() != null)
                .collect(Collectors.toMap(
                        ScientificArticle::getDoi,
                        article -> article,
                        (existing, replacement) -> existing
                ))
                .values()
                .stream()
                .collect(Collectors.toList());
    }

    private static final String PLANT_BREEDING_CONTEXT =
            "(" +
                    "\"plant breeding\" OR " +
                    "\"crop breeding\" OR " +
                    "\"breeding program\" OR " +
                    "\"crop improvement\" OR " +
                    "\"genomic selection\" OR " +
                    "\"marker-assisted selection\" OR " +
                    "cultivar OR " +
                    "germplasm" +
                    ")";
}