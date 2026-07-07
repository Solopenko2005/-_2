package searchengine.dto;

import lombok.Data;
import java.util.List;

@Data
public class TopicGroupDto {
    private int id;
    private String title;
    private String contentSummary;
    private int frequency;
    private int siteCount;
    private List<String> keyLemmas;
    private List<TopicDto> topics;
}

