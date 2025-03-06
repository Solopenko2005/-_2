package searchengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "indexing-settings")
public class IndexingSettings {

    private List<SiteConfig> sites;
    private String userAgent;
    private String referer;


    @Getter
    @Setter
    public static class Site {
        private String url; // URL сайта
        private String name; // Название сайта
    }
    public List<SiteConfig> getSites() {
        return sites;
    }

    public void setSites(List<SiteConfig> sites) {
        this.sites = sites;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getReferer() {
        return referer;
    }

    public void setReferer(String referer) {
        this.referer = referer;
    }

    public static class SiteConfig {
        private String url;
        private String name;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
