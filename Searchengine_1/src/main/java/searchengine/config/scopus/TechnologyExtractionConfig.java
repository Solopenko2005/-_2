package searchengine.config.scopus;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Конфигурация для извлечения технологий
 */
@Data
@Component
@ConfigurationProperties(prefix = "technology-extraction")
public class TechnologyExtractionConfig {

    /**
     * Минимальная длина слова для рассмотрения как технологии
     */
    private int minWordLength = 3;

    /**
     * Максимальное количество слов в названии технологии (n-gram)
     */
    private int maxNgramSize = 4;

    /**
     * Минимальная частота упоминания для включения в результат
     */
    private int minFrequency = 1;

    /**
     * Технологические маркеры для английского языка
     */
    private List<String> techMarkersEn;

    /**
     * Технологические маркеры для русского языка
     */
    private List<String> techMarkersRu;
}