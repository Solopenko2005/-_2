package searchengine.dto;

import lombok.Data;

@Data
public class TopicStatisticsDto {
    private int totalTopics;
    private int uniqueTopics;
    private long totalLemmaCount;
    private String mostFrequentTopic;
    private double averageLemmaPerTopic;
}