package searchengine.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Data
@Getter
@Setter
public class TopicFrequencyDto {
    private int groupId;
    private String groupTitle;
    private String topicTitle;
    private int frequency;
    private int siteCount;
    private List<TopicLinkDto> links;
    private List<String> siteNames; // Добавьте это поле

    // Конструкторы
    public TopicFrequencyDto() {}

    public TopicFrequencyDto(int groupId, String groupTitle, String topicTitle,
                             int frequency, int siteCount, List<TopicLinkDto> links,
                             List<String> siteNames) {
        this.groupId = groupId;
        this.groupTitle = groupTitle;
        this.topicTitle = topicTitle;
        this.frequency = frequency;
        this.siteCount = siteCount;
        this.links = links;
        this.siteNames = siteNames;
    }
}