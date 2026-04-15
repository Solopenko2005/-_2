package searchengine.services.scopus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import searchengine.config.scopus.ScientificSourcesConfig;
import searchengine.model.scopus.ScientificArticle;
import searchengine.services.scopus.TechnologyExtractionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Сервис для работы с Scopus API
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
                .build();
        this.objectMapper = new ObjectMapper();
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
                            .queryParam("query", query)
                            .queryParam("count", config.getScopus().getMaxResults())
                            .queryParam("start", 0)
                            .build())
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .header("X-ELS-APIKey", apiKey)
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
}