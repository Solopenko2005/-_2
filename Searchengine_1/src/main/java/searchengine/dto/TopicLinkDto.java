package searchengine.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Data
@Getter
@Setter
public class TopicLinkDto {
    private String url;
    private String siteName;
    private String siteUrl; // Добавьте это поле
    private String pagePath;
    private String topicTitle;
    private LocalDateTime createdAt;
    private int lemmaCount;

    // Конструкторы
    public TopicLinkDto() {}

    public TopicLinkDto(String url, String siteName, String siteUrl, String pagePath,
                        String topicTitle, LocalDateTime createdAt, int lemmaCount) {
        this.url = url;
        this.siteName = siteName;
        this.siteUrl = siteUrl;
        this.pagePath = pagePath;
        this.topicTitle = topicTitle;
        this.createdAt = createdAt;
        this.lemmaCount = lemmaCount;
    }
}