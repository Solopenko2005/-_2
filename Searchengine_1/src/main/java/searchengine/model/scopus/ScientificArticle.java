package searchengine.model.scopus;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScientificArticle {

    // Базовые идентификаторы
    private String sourceId;           // dc:identifier
    private String sourceName;         // "SCOPUS"
    private String doi;                // prism:doi

    // Метаданные статьи
    private String title;              // dc:title
    private String abstractText;       // dc:description
    private List<String> keywords;     // authkeywords
    private String language;           // language
    private Integer publicationYear;   // из prism:coverDate
    private List<String> authors;      // author

    // Информация о публикации
    private String publicationName;    // prism:publicationName
    private String publisher;          // dc:publisher
    private String documentType;       // subtypeDescription
    private String openAccess;         // openaccess
    private Integer citedByCount;      // citedby-count
    private String link;               // link/href

    // Извлеченные технологии
    private Map<String, Integer> extractedTechnologies;
}