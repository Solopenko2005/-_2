package searchengine.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
@Getter
@Setter
public class SearchResponse {
    private boolean result;
    private String error;
    private int count;
    private List<SearchResult> data;
}
