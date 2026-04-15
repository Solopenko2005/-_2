package searchengine.services.scopus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import searchengine.config.scopus.ScientificSourcesConfig;
import searchengine.model.scopus.ScientificArticle;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис для работы с Scopus API
 * Автоматически находит статьи по темам семеноводства и селекции
 */
@Slf4j
@Service
public class ScopusApiService {

    private final ScientificSourcesConfig config;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final TechnologyExtractionService extractionService;

    public ScopusApiService(
            ScientificSourcesConfig config,
            TechnologyExtractionService extractionService) {
        this.config = config;
        this.extractionService = extractionService;
        this.webClient = WebClient.builder()
                .baseUrl(config.getScopus().getBaseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-ELS-APIKey", config.getScopus().getApiKey())
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Автоматический поиск статей по темам семеноводства и селекции
     * Использует предопределённые поисковые запросы из конфигурации
     * @return Список найденных статей
     */
    public List<ScientificArticle> searchSeedBreedingArticles() {
        log.info("Searching Scopus articles for seed breeding topics automatically");

        List<String> queries = config.getSeedBreedingTopics().getSearchQueriesEn();
        if (queries == null || queries.isEmpty()) {
            // Запросы по умолчанию для семеноводства и селекции
            queries = getDefaultSeedBreedingQueries();
            log.info("Using default seed breeding queries: {}", queries);
        }

        List<ScientificArticle> allArticles = new ArrayList<>();

        for (String query : queries) {
            log.info("Executing query: {}", query);
            List<ScientificArticle> articles = searchArticles(query);
            allArticles.addAll(articles);
        }

        // Удаляем дубликаты по sourceId
        return allArticles.stream()
                .collect(Collectors.toMap(
                        ScientificArticle::getSourceId,
                        article -> article,
                        (existing, replacement) -> existing
                ))
                .values()
                .stream()
                .collect(Collectors.toList());
    }

    /**
     * Поиск статей по ключевым словам
     * @param query Поисковый запрос (например, "seed breeding" OR "plant genetics")
     * @return Список найденных статей
     */
    public List<ScientificArticle> searchArticles(String query) {
        log.info("Searching Scopus articles with query: {}", query);

        String apiKey = config.getScopus().getApiKey();
        if (apiKey == null || apiKey.equals("your_scopus_api_key_here")) {
            log.error("Scopus API key is not configured. Please set SCOPUS_API_KEY environment variable.");
            return Collections.emptyList();
        }

        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/content/search/scopus")
                            .queryParam("query", query)
                            .queryParam("count", config.getScopus().getMaxResults())
                            .queryParam("start", 0)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseScopusResponse(response);

        } catch (Exception e) {
            log.error("Error searching Scopus articles: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Парсинг ответа от Scopus API
     */
    private List<ScientificArticle> parseScopusResponse(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode entries = root.path("search-results").path("entry");

            List<ScientificArticle> articles = new ArrayList<>();

            if (entries.isArray()) {
                for (JsonNode entry : entries) {
                    ScientificArticle article = ScientificArticle.builder()
                            .sourceId(entry.path("dc:identifier").asText(""))
                            .sourceName("SCOPUS")
                            .title(entry.path("dc:title").asText(""))
                            .abstractText(entry.path("dc:description").asText(""))
                            .keywords(extractKeywords(entry))
                            .language("en") // Scopus статьи преимущественно на английском
                            .publicationYear(extractPublicationYear(entry))
                            .authors(extractAuthors(entry))
                            .doi(entry.path("prism:doi").asText(""))
                            .build();

                    // Извлечение технологий из статьи
                    var extractionResult = extractionService.extractTechnologies(
                            article.getTitle(),
                            article.getAbstractText(),
                            article.getKeywords(),
                            article.getLanguage()
                    );
                    article.setExtractedTechnologies(extractionResult.getTechnologyFrequency());

                    articles.add(article);
                }
            }

            log.info("Parsed {} articles from Scopus", articles.size());
            return articles;

        } catch (Exception e) {
            log.error("Error parsing Scopus response: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private List<String> extractKeywords(JsonNode entry) {
        List<String> keywords = new ArrayList<>();
        JsonNode authKeywords = entry.path("authkeywords");
        if (authKeywords.isArray()) {
            for (JsonNode keyword : authKeywords) {
                keywords.add(keyword.asText(""));
            }
        }
        return keywords;
    }

    private Integer extractPublicationYear(JsonNode entry) {
        String pubDate = entry.path("prism:coverDate").asText("");
        if (pubDate.length() >= 4) {
            try {
                return Integer.parseInt(pubDate.substring(0, 4));
            } catch (NumberFormatException e) {
                return null;
            }
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
                if (!givenName.isEmpty() || !surname.isEmpty()) {
                    authors.add((givenName + " " + surname).trim());
                }
            }
        }
        return authors;
    }

    /**
     * Возвращает список поисковых запросов по умолчанию для семеноводства и селекции
     */
    private List<String> getDefaultSeedBreedingQueries() {
        return List.of(
                "seed breeding AND genetics",
                "plant breeding AND seed production",
                "crop improvement AND seed technology",
                "seed quality AND germination",
                "hybrid seed production",
                "seed treatment AND protection",
                "molecular breeding AND seeds",
                "seed storage AND preservation",
                "seed certification AND standards",
                "conventional breeding AND modern varieties"
        );
    }
}