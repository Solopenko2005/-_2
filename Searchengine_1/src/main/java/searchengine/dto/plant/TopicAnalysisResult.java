package searchengine.dto.plant;

import searchengine.model.Topic;

import java.util.HashMap;
import java.util.Map;

public class TopicAnalysisResult {
    private Topic topic;
    private int seedKeywordsFrequency;
    private int techKeywordsFrequency;
    private int totalFrequency;
    private Map<String, Integer> contentKeywordMatches;

    public TopicAnalysisResult(Topic topic) {
        this.topic = topic;
        this.seedKeywordsFrequency = 0;
        this.techKeywordsFrequency = 0;
        this.totalFrequency = 0;
        this.contentKeywordMatches = new HashMap<>();
    }

    // Getters and setters
    public Topic getTopic() { return topic; }
    public void setTopic(Topic topic) { this.topic = topic; }
    public int getSeedKeywordsFrequency() { return seedKeywordsFrequency; }
    public void setSeedKeywordsFrequency(int seedKeywordsFrequency) { this.seedKeywordsFrequency = seedKeywordsFrequency; }
    public int getTechKeywordsFrequency() { return techKeywordsFrequency; }
    public void setTechKeywordsFrequency(int techKeywordsFrequency) { this.techKeywordsFrequency = techKeywordsFrequency; }
    public int getTotalFrequency() { return totalFrequency; }
    public void setTotalFrequency(int totalFrequency) { this.totalFrequency = totalFrequency; }
    public Map<String, Integer> getContentKeywordMatches() { return contentKeywordMatches; }
    public void setContentKeywordMatches(Map<String, Integer> contentKeywordMatches) { this.contentKeywordMatches = contentKeywordMatches; }
}