package searchengine.dto;

import lombok.Data;

@Data
public class TopicReferenceDto {
    private String url;
    private String siteName;
    private String pageTitle;
    private String originalTitle;   // Оригинальное название темы на сайте
}
