package searchengine.config.scopus;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Конфигурация для подключения к внешним API
 */
@Data
@Component
@ConfigurationProperties(prefix = "scientific.sources")
public class ScientificSourcesConfig {

    /**
     * Настройки Scopus API
     */
    private ScopusConfig scopus = new ScopusConfig();

    /**
     * Настройки Web of Science API
     */
    private WosConfig wos = new WosConfig();

    /**
     * Настройки eLibrary API
     */
    private ElibraryConfig elibrary = new ElibraryConfig();

    /**
     * Темы для поиска статей по семеноводству и селекции
     */
    private SeedBreedingTopics seedBreedingTopics = new SeedBreedingTopics();

    @Data
    public static class ScopusConfig {
        private String apiKey;
        private String baseUrl = "https://api.elsevier.com/content/search/scopus";
        private int maxResults = 100;
    }

    @Data
    public static class WosConfig {
        private String apiKey;
        private String baseUrl = "https://api.clarivate.com/api/wos";
        private int maxResults = 100;
    }

    @Data
    public static class ElibraryConfig {
        private String apiKey;
        private String baseUrl = "https://elibrary.ru/query_api.asp";
        private int maxResults = 100;
    }

    /**
     * Конфигурация тем для поиска статей по семеноводству и селекции
     */
    @Data
    public static class SeedBreedingTopics {
        /**
         * Ключевые слова на английском языке для поиска в Scopus
         */
        private List<String> keywordsEn;

        /**
         * Ключевые слова на русском языке для поиска в eLibrary
         */
        private List<String> keywordsRu;

        /**
         * Предопределённые поисковые запросы для Scopus
         */
        private List<String> searchQueriesEn;

        /**
         * Предопределённые поисковые запросы для eLibrary
         */
        private List<String> searchQueriesRu;
    }
}