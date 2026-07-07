package searchengine.dto;

import lombok.Data;

import java.util.List;

@Data
public class TopicFrequencyResponse {
    private boolean result;
    private List<TopicGroupDto> data;
    private String error;
}
