package searchengine.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TopicDto {
    private int id;
    private String title;
    private String content;
    private int orderNum;
    private int lemmaCount;
    private String pagePath;
    private String siteUrl;
    private String siteName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}