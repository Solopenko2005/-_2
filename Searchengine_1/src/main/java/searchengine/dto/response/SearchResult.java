package searchengine.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SearchResult {
    private String site;
    private String siteName;
    private String uri;
    private String title;
    private String snippet;
    private double relevance;
}

