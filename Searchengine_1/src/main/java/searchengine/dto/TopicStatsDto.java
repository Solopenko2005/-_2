package searchengine.dto;

import lombok.Data;
import java.util.List;

@Data
public class TopicStatsDto {
    private String theme;           // Объединенное название темы
    private int frequency;          // Частота использования (сколько раз встречается)
    private int siteCount;          // Число сайтов с упоминанием
    private List<TopicReferenceDto> references; // Ссылки на все упоминания
    private List<String> originalTitles; // Оригинальные названия тем
    private List<String> siteNames;     // Названия сайтов
}

