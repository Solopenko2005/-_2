package searchengine.config.scopus;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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
}