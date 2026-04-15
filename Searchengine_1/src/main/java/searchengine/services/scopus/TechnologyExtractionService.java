package searchengine.services.scopus;

import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.InvalidFormatException;
import searchengine.config.scopus.TechnologyExtractionConfig;
import searchengine.model.scopus.TechnologyExtractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Сервис для извлечения технологий из текста с использованием гибридного подхода:
 * 1. NLP-обработка (токенизация, POS-тегирование)
 * 2. Извлечение n-грамм
 * 3. Фильтрация по технологическим маркерам
 * 4. Частотный анализ
 */
@Service
public class TechnologyExtractionService {

    private static final Logger log = LoggerFactory.getLogger(TechnologyExtractionService.class);

    private final TechnologyExtractionConfig config;

    // Модели OpenNLP для английского языка
    private SentenceModel enSentenceModel;
    private TokenizerModel enTokenizerModel;
    private POSModel enPosModel;

    // Модели OpenNLP для русского языка (будут загружены при наличии)
    private SentenceModel ruSentenceModel;
    private TokenizerModel ruTokenizerModel;
    private POSModel ruPosModel;

    // Паттерн для очистки текста от специальных символов
    private static final Pattern CLEAN_PATTERN = Pattern.compile("[^a-zA-Zа-яА-ЯёЁ0-9\\s-]");

    public TechnologyExtractionService(TechnologyExtractionConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {
        try {
            // Загрузка моделей для английского языка
            enSentenceModel = loadSentenceModel("opennlp/en-sent.bin");
            enTokenizerModel = loadTokenizerModel("opennlp/en-token.bin");
            enPosModel = loadPosModel("opennlp/en-pos-maxent.bin");
            log.info("English OpenNLP models loaded successfully");
        } catch (IOException e) {
            log.warn("Could not load English OpenNLP models. Will use basic tokenization. Error: {}", e.getMessage());
        }

        // Попытка загрузить русские модели (если они есть в resources/opennlp)
        try {
            ruSentenceModel = loadSentenceModel("opennlp/ru-sent.bin");
            ruTokenizerModel = loadTokenizerModel("opennlp/ru-token.bin");
            ruPosModel = loadPosModel("opennlp/ru-pos.bin");
            log.info("Russian OpenNLP models loaded successfully");
        } catch (IOException e) {
            log.warn("Russian OpenNLP models not found. Will use basic tokenization for Russian text. Error: {}", e.getMessage());
        }
    }

    private SentenceModel loadSentenceModel(String path) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Model not found: " + path);
            }
            return new SentenceModel(is);
        }
    }

    private TokenizerModel loadTokenizerModel(String path) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Model not found: " + path);
            }
            return new TokenizerModel(is);
        }
    }

    private POSModel loadPosModel(String path) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Model not found: " + path);
            }
            return new POSModel(is);
        }
    }

    /**
     * Извлечение технологий из текста статьи
     * @param title Заголовок статьи
     * @param abstractText Аннотация
     * @param keywords Ключевые слова
     * @param language Язык статьи (en или ru)
     * @return Результат извлечения технологий
     */
    public TechnologyExtractionResult extractTechnologies(
            String title,
            String abstractText,
            List<String> keywords,
            String language) {

        log.info("Extracting technologies from article in language: {}", language);

        // Объединяем весь текст
        StringBuilder fullText = new StringBuilder();
        if (title != null && !title.isEmpty()) {
            fullText.append(title).append(". ");
        }
        if (abstractText != null && !abstractText.isEmpty()) {
            fullText.append(abstractText).append(". ");
        }
        if (keywords != null && !keywords.isEmpty()) {
            fullText.append(String.join(", ", keywords));
        }

        if (fullText.length() == 0) {
            return TechnologyExtractionResult.builder()
                    .technologies(Collections.emptyList())
                    .technologyFrequency(Collections.emptyMap())
                    .sourceText("")
                    .language(language)
                    .build();
        }

        String text = fullText.toString();

        // Определение списка маркеров в зависимости от языка
        List<String> techMarkers = "ru".equalsIgnoreCase(language)
                ? config.getTechMarkersRu()
                : config.getTechMarkersEn();

        // Токенизация и обработка текста
        List<String> tokens = tokenize(text, language);

        // Извлечение n-грамм и фильтрация
        Map<String, Integer> technologyFrequency = extractAndCountTechnologies(tokens, techMarkers, language);

        // Фильтрация по минимальной частоте
        Map<String, Integer> filteredTechnologies = technologyFrequency.entrySet().stream()
                .filter(entry -> entry.getValue() >= config.getMinFrequency())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        List<String> technologies = new ArrayList<>(filteredTechnologies.keySet());
        technologies.sort((a, b) -> filteredTechnologies.get(b).compareTo(filteredTechnologies.get(a)));

        log.info("Extracted {} technologies: {}", technologies.size(), technologies);

        return TechnologyExtractionResult.builder()
                .technologies(technologies)
                .technologyFrequency(filteredTechnologies)
                .sourceText(text)
                .language(language)
                .build();
    }

    /**
     * Токенизация текста
     */
    private List<String> tokenize(String text, String language) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }

        // Очистка текста
        String cleanedText = CLEAN_PATTERN.matcher(text).replaceAll(" ").toLowerCase();

        if ("en".equalsIgnoreCase(language) && enTokenizerModel != null) {
            try {
                TokenizerME tokenizer = new TokenizerME(enTokenizerModel);
                return Arrays.asList(tokenizer.tokenize(cleanedText));
            } catch (Exception e) {
                log.warn("Error tokenizing with OpenNLP, using basic split: {}", e.getMessage());
            }
        } else if ("ru".equalsIgnoreCase(language) && ruTokenizerModel != null) {
            try {
                TokenizerME tokenizer = new TokenizerME(ruTokenizerModel);
                return Arrays.asList(tokenizer.tokenize(cleanedText));
            } catch (Exception e) {
                log.warn("Error tokenizing Russian text with OpenNLP, using basic split: {}", e.getMessage());
            }
        }

        // Базовая токенизация
        return Arrays.asList(cleanedText.split("\\s+"));
    }

    /**
     * Извлечение технологий из токенов с использованием маркеров
     */
    private Map<String, Integer> extractAndCountTechnologies(
            List<String> tokens,
            List<String> techMarkers,
            String language) {

        Map<String, Integer> frequencyMap = new HashMap<>();

        if (tokens.isEmpty() || techMarkers.isEmpty()) {
            return frequencyMap;
        }

        // Извлечение unigrams (отдельные слова-маркеры)
        for (String token : tokens) {
            if (token.length() >= config.getMinWordLength() && techMarkers.contains(token)) {
                frequencyMap.merge(token, 1, Integer::sum);
            }
        }

        // Извлечение bigrams, trigrams, 4-grams
        for (int n = 2; n <= config.getMaxNgramSize(); n++) {
            for (int i = 0; i <= tokens.size() - n; i++) {
                List<String> ngram = tokens.subList(i, i + n);
                String ngramStr = String.join(" ", ngram);

                // Проверка: содержит ли n-грамма хотя бы один технологический маркер
                boolean containsMarker = ngram.stream().anyMatch(techMarkers::contains);

                if (containsMarker && isValidTechnologyPhrase(ngram, language)) {
                    frequencyMap.merge(ngramStr, 1, Integer::sum);
                }
            }
        }

        return frequencyMap;
    }

    /**
     * Проверка валидности фразы как технологии
     * (содержит существительные и прилагательные, не является стоп-словом)
     */
    private boolean isValidTechnologyPhrase(List<String> phrase, String language) {
        // Простая эвристика: фраза должна содержать хотя бы 2 слова
        // и не состоять только из предлогов/союзов
        if (phrase.size() < 2) {
            return true;
        }

        Set<String> stopWords = getStopWords(language);

        // Проверяем, что не все слова являются стоп-словами
        long nonStopWords = phrase.stream()
                .filter(word -> !stopWords.contains(word.toLowerCase()))
                .count();

        return nonStopWords >= 1;
    }

    /**
     * Стоп-слова для английского и русского языков
     */
    private Set<String> getStopWords(String language) {
        Set<String> stopWords = new HashSet<>();

        if ("en".equalsIgnoreCase(language)) {
            stopWords.addAll(Arrays.asList(
                    "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
                    "of", "with", "by", "from", "is", "are", "was", "were", "be", "been",
                    "being", "have", "has", "had", "do", "does", "did", "will", "would",
                    "could", "should", "may", "might", "must", "shall", "can", "need",
                    "this", "that", "these", "those", "it", "its", "as", "if", "when"
            ));
        } else if ("ru".equalsIgnoreCase(language)) {
            stopWords.addAll(Arrays.asList(
                    "и", "в", "во", "не", "что", "он", "на", "я", "с", "со", "как", "а",
                    "то", "все", "она", "так", "его", "но", "да", "ты", "к", "у", "же",
                    "вы", "за", "бы", "по", "только", "ее", "мне", "было", "вот", "от",
                    "меня", "еще", "нет", "о", "из", "ему", "теперь", "когда", "даже",
                    "ну", "этого", "этом", "этот", "которого", "потому", "этой", "если"
            ));
        }

        return stopWords;
    }
}