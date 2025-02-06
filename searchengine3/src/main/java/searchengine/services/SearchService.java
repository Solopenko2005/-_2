package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.SearchResponse;
import searchengine.model.Page;
import searchengine.model.SearchIndex;
import searchengine.model.Lemma;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.utils.Lemmatizer;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private final PageRepository pageRepository;
    @Autowired
    private LemmaService lemmaService;
    private final IndexRepository indexRepository;
    private final SiteRepository siteRepository;
    @Autowired
    private LemmaRepository lemmaRepository;

    @Autowired
    public SearchService(PageRepository pageRepository,
                         IndexRepository indexRepository, SiteRepository siteRepository, LemmaRepository lemmaRepository) {
        this.pageRepository = pageRepository;
        this.indexRepository = indexRepository;
        this.siteRepository = siteRepository;
        this.lemmaRepository = lemmaRepository;
    }

    // Метод для расчета релевантности вручную
    public float getRelevanceForPage(Page page, List<String> lemmas) {
        List<SearchIndex> searchIndexes = indexRepository.findIndexesForPageAndLemmas(page, new HashSet<>(lemmas));

        float relevance = 0.0f;
        for (SearchIndex index : searchIndexes) {
            relevance += index.getRanking(); // или другая логика вычисления
        }
        return relevance;
    }

    public SearchResponse search(String query, String site, int offset, int limit) throws IOException {
        if (query == null || query.trim().isEmpty()) {
            return SearchResponse.error("Задан пустой поисковый запрос");
        }

        // Шаг 1: Разбиваем поисковый запрос на леммы
        Set<String> lemmas = Lemmatizer.extractLemmas(query);
        System.out.println("Извлечённые леммы: " + lemmas); // выводим леммы, чтобы понять, как они извлекаются
        if (lemmas.isEmpty()) {
            return SearchResponse.error("По запросу не найдено значимых слов");
        }

        // Шаг 2: Обновляем частоты лемм и выводим
        Map<String, Integer> lemmaFrequency = updateLemmaFrequenciesAndReturn(lemmas);
        System.out.println("Частоты лемм: " + lemmaFrequency);
        if (lemmaFrequency.isEmpty()) {
            return SearchResponse.error("Не удалось найти леммы для поиска");
        }

        List<String> sortedLemmas = lemmaFrequency.entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry::getValue)) // сортируем по возрастанию частоты
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (sortedLemmas.isEmpty()) {
            return SearchResponse.error("Не удалось найти леммы для поиска.");
        }

        // Шаг 3: Поиск страниц по леммам
        List<Page> matchingPages = null;
        for (int i = 0; i < sortedLemmas.size(); i++) {
            String lemma = sortedLemmas.get(i);
            List<Page> pagesForLemma = indexRepository.findPagesByLemma(lemma);
            System.out.println("Найдено страниц для леммы \"" + lemma + "\": " + pagesForLemma.size());
            if (i == 0) {
                matchingPages = pagesForLemma;
            } else {
                matchingPages.retainAll(pagesForLemma); // оставляем только пересекающиеся страницы
            }
            if (matchingPages.isEmpty()) break; // если страниц не осталось, выходим из цикла
        }

        if (matchingPages == null || matchingPages.isEmpty()) {
            return SearchResponse.success(0, Collections.emptyList()); // возвращаем пустой список
        }

        // Шаг 4: Вычисляем релевантность для каждой страницы
        Map<Page, Float> relevanceMap = new HashMap<>();
        float maxRelevance = 0;

        for (Page page : matchingPages) {
            float relevance = getRelevanceForPage(page, sortedLemmas);
            relevanceMap.put(page, relevance);
            maxRelevance = Math.max(maxRelevance, relevance); // сохраняем максимальную релевантность
        }

        // Нормализуем релевантность
        float finalMaxRelevance = maxRelevance;
        List<SearchResponse.SearchResult> results = relevanceMap.entrySet().stream()
                .map(entry -> {
                    Page page = entry.getKey();
                    float relativeRelevance = (finalMaxRelevance > 0) ? entry.getValue() / finalMaxRelevance : 0;
                    return new SearchResponse.SearchResult(
                            page.getSite().getUrl(),
                            page.getSite().getName(),
                            page.getPath(),
                            createSnippet(page.getContent(), lemmas),
                            relativeRelevance
                    );
                })
                .sorted(Comparator.comparing(SearchResponse.SearchResult::getRelevance).reversed()) // сортируем по убыванию релевантности
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());

        return SearchResponse.success(matchingPages.size(), results);
    }

    // Метод для создания сниппета с выделением найденных лемм
    private String createSnippet(String content, Set<String> lemmas) {
        if (content == null || content.isEmpty()) {
            return "";
        }

        // Разбиваем текст на слова
        String[] words = content.split("\\s+");
        StringBuilder snippet = new StringBuilder();
        int wordCount = 0;
        int maxSnippetWords = 30; // Ограничение на количество слов в сниппете

        for (String word : words) {
            String normalizedWord = word.toLowerCase().replaceAll("[^а-яА-Я]", ""); // Убираем знаки препинания
            if (lemmas.contains(normalizedWord)) {
                snippet.append("<b>").append(word).append("</b> "); // Выделяем найденные слова жирным
            } else {
                snippet.append(word).append(" ");
            }

            wordCount++;
            if (wordCount >= maxSnippetWords) {
                break; // Ограничиваем длину сниппета
            }
        }

        return snippet.toString().trim() + "..."; // Добавляем троеточие в конце
    }
    // Метод для обновления частоты лемм
    private Map<String, Integer> updateLemmaFrequenciesAndReturn(Set<String> lemmas) {
        Map<String, Integer> lemmaFrequency = new HashMap<>();

        for (String lemma : lemmas) {
            Lemma existingLemma = lemmaRepository.findByLemma(lemma);
            if (existingLemma != null) {
                // Если лемма существует, увеличиваем частоту
                existingLemma.setFrequency(existingLemma.getFrequency() + 1);
                lemmaRepository.save(existingLemma);
                lemmaFrequency.put(lemma, existingLemma.getFrequency());
            } else {
                // Если лемма новая, создаём её
                Lemma newLemma = new Lemma();
                newLemma.setLemma(lemma);
                newLemma.setFrequency(1);
                // Здесь нужно установить сайт для леммы (если у вас есть такая логика)
                lemmaRepository.save(newLemma);
                lemmaFrequency.put(lemma, 1);
            }
        }

        return lemmaFrequency;
    }
}
