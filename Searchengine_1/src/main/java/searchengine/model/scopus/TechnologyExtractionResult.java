package searchengine.model.scopus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Результат извлечения технологий из текста
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TechnologyExtractionResult {

    /**
     * Список извлеченных технологий
     */
    private List<String> technologies;

    /**
     * Частота упоминания каждой технологии
     */
    private java.util.Map<String, Integer> technologyFrequency;

    /**
     * Исходный текст, из которого были извлечены технологии
     */
    private String sourceText;

    /**
     * Язык обработки (en, ru)
     */
    private String language;
}