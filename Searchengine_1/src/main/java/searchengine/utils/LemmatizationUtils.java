package searchengine.utils;

import searchengine.services.Lemmatizer;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class LemmatizationUtils {

    private final Lemmatizer lemmatizer;

    public LemmatizationUtils(Lemmatizer lemmatizer) {
        this.lemmatizer = lemmatizer;
    }

    public List<String> getQueryLemmas(String query) {
        Map<String, Integer> lemmasMap = lemmatizer.getLemmas(query);
        return new ArrayList<>(lemmasMap.keySet());
    }
}