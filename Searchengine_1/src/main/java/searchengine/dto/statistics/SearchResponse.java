package searchengine.dto.statistics;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
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
        private final Status status; // Добавлено поле для статуса сайта
        private final LocalDateTime statusTime; // Добавлено поле для времени статуса
        private final String lastError; // Добавлено поле для последней ошибки

        public SearchResult(
                @JsonProperty("site") String site,
                @JsonProperty("siteName") String siteName,
                @JsonProperty("uri") String uri,
                @JsonProperty("title") String title,
                @JsonProperty("relevance") float relevance,
                @JsonProperty("status") Status status, // Добавлен параметр для статуса
                @JsonProperty("statusTime") LocalDateTime statusTime, // Добавлен параметр для времени статуса
                @JsonProperty("lastError") String lastError) { // Добавлен параметр для последней ошибки
            this.site = site;
            this.siteName = siteName;
            this.uri = uri;
            this.title = title;
            this.relevance = relevance;
            this.status = status;
            this.statusTime = statusTime;
            this.lastError = lastError;
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

        public Status getStatus() {
            return status;
        }

        public LocalDateTime getStatusTime() {
            return statusTime;
        }

        public String getLastError() {
            return lastError;
        }
    }

    public enum Status {
        INDEXING,
        INDEXED,
        FAILED
    }
}