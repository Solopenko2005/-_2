package searchengine.dto;

import lombok.Data;
import java.util.List;

@Data
public class PopularTopicsResponse {
    private boolean result;
    private TopicStatisticsDto statistics;
    private List<TopicDto> data;
    private String error;
}