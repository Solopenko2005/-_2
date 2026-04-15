package searchengine.model.scopus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Модель статьи из научной базы данных (Scopus, WoS, eLibrary)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScientificArticle {

    /**
     * Уникальный идентификатор статьи в источнике
     */
    private String sourceId;

    /**
     * Название источника (SCOPUS, WOS, ELIBRARY)
     */
    private String sourceName;

    /**
     * Заголовок/тема статьи
     */
    private String title;

    /**
     * Аннотация статьи
     */
    private String abstractText;

    /**
     * Ключевые слова статьи
     */
    private List<String> keywords;

    /**
     * Извлеченные технологии с частотой упоминания
     * Ключ - название технологии, Значение - количество упоминаний
     */
    private Map<String, Integer> extractedTechnologies;

    /**
     * Язык статьи (en, ru)
     */
    private String language;

    /**
     * Год публикации
     */
    private Integer publicationYear;

    /**
     * Авторы статьи
     */
    private List<String> authors;

    /**
     * DOI статьи
     */
    private String doi;
}