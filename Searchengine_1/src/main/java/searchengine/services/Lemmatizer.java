package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Service;
import searchengine.config.IndexingState;
import searchengine.repository.LemmaRepository;

import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class Lemmatizer {

    private static final List<String> STOP_WORDS = Arrays.asList(
            "и", "в", "на", "с", "по", "за", "из", "у", "для"
    );

    private final LuceneMorphology luceneMorph;
    private final IndexingState indexingState;

    public Map<String, Integer> getQueryLemmas(String text) {
        if (shouldInterrupt()) return Collections.emptyMap();

        Map<String, Integer> lemmas = new HashMap<>();
        processText(text, lemmas);
        return lemmas;
    }
    @Deprecated
    public Map<String, Integer> getLemmas(String text) {
        return getQueryLemmas(text);
    }

    public List<String> getWordLemmas(String word) {
        if (shouldInterrupt() || word == null || word.isEmpty()) return Collections.emptyList();

        word = word.toLowerCase().replaceAll("[^а-яё]", "");
        return getNormalForms(word);
    }


    public Map<String, Integer> extractLemmasWithRank(String text) {
        if (shouldInterrupt()) return Collections.emptyMap();

        Map<String, Integer> lemmas = new HashMap<>();
        processText(text, lemmas);
        return lemmas;
    }

    private void processText(String text, Map<String, Integer> lemmas) {
        Arrays.stream(text.toLowerCase().split("\\s+"))
                .map(this::cleanWord)
                .filter(word -> !word.isEmpty())
                .forEach(word -> processWord(word, lemmas));
    }

    private void processWord(String word, Map<String, Integer> lemmas) {
        getNormalForms(word).stream()
                .filter(lemma -> !isStopWord(lemma))
                .forEach(lemma -> lemmas.merge(lemma, 1, Integer::sum));
    }

    private List<String> getNormalForms(String word) {
        try {
            return luceneMorph.getNormalForms(word);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String cleanWord(String word) {
        return word.replaceAll("[^а-яё]", "").trim();
    }

    private boolean isStopWord(String lemma) {
        return STOP_WORDS.contains(lemma);
    }

    private boolean shouldInterrupt() {
        return indexingState.isStopRequested();
    }
}