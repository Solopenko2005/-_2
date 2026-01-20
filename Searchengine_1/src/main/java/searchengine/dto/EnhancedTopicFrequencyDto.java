package searchengine.dto;

import lombok.Data;
import java.util.List;

@Data
public class EnhancedTopicFrequencyDto {
    private int groupId;
    private String groupTitle;
    private String normalizedTitle; // Нормализованный заголовок
    private int frequency;
    private int siteCount;
    private List<String> siteNames; // Список названий сайтов
    private List<String> keyLemmas; // Ключевые леммы
    private List<TopicLinkDto> links;
    private String contentSummary; // Краткое описание
}