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
    private List<Site> sites; // Список сайтов для индексации
    private String userAgent; // User-Agent для запросов
    private String referer; // Referer для запросов
    private int delayBetweenRequests = 500; // Задержка между запросами в миллисекундах (по умолчанию 500 мс)
    private int maxRecursionDepth = 10; // Максимальная глубина рекурсии (по умолчанию 10)
    private int maxPagesPerSite = 1000; // Максимальное количество страниц для одного сайта (по умолчанию 1000)

    @Getter
    @Setter
    public static class Site {
        private String url; // URL сайта
        private String name; // Название сайта
    }
}