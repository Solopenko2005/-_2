package searchengine.dto.response;

import java.util.List;

public class SearchResponse {
    private boolean result;
    private String error;
    private int count;
    private List<SearchResult> data;

    // Геттеры и сеттеры
    public boolean isResult() {
        return result;
    }

    public void setResult(boolean result) {
        this.result = result;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public List<SearchResult> getData() {
        return data;
    }

    public void setData(List<SearchResult> data) {
        this.data = data;
    }
}
