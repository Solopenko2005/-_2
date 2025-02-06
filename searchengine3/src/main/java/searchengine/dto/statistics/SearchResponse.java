package searchengine.dto.statistics;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;


@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchResponse {

    private final boolean result;
    private final String error;
    private final int count;
    private final List<SearchResult> data;

    private SearchResponse(boolean result, String error, int count, List<SearchResult> data) {
        this.result = result;
        this.error = error;
        this.count = count;
        this.data = data;
    }

    public static SearchResponse success(int count, List<SearchResult> data) {
        return new SearchResponse(true, null, count, data);
    }

    public static SearchResponse error(String errorMessage) {
        return new SearchResponse(false, errorMessage, 0, null);
    }

    public boolean isResult() {
        return result;
    }

    public String getError() {
        return error;
    }

    public int getCount() {
        return count;
    }

    public List<SearchResult> getData() {
        return data;
    }

    public static class SearchResult {
        private final String site;
        private final String siteName;
        private final String uri;
        private final String title;
        private final float relevance;

        public SearchResult(
                @JsonProperty("site") String site,
                @JsonProperty("siteName") String siteName,
                @JsonProperty("uri") String uri,
                @JsonProperty("title") String title,
                @JsonProperty("relevance") float relevance) {
            this.site = site;
            this.siteName = siteName;
            this.uri = uri;
            this.title = title;
            this.relevance = relevance;
        }

        public String getSite() {
            return site;
        }

        public String getSiteName() {
            return siteName;
        }

        public String getUri() {
            return uri;
        }

        public String getTitle() {
            return title;
        }


        public float getRelevance() {
            return relevance;
        }
    }
}
