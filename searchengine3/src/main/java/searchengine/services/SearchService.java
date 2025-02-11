package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.SearchResponse;
import searchengine.model.Page;
import searchengine.model.SearchIndex;
import searchengine.model.Lemma;
import searchengine.model.Site;
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
    private final IndexRepository indexRepository;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;

    @Autowired
    public SearchService(PageRepository pageRepository,
                         IndexRepository indexRepository,
                         SiteRepository siteRepository,
                         LemmaRepository lemmaRepository) {
        this.pageRepository = pageRepository;
        this.indexRepository = indexRepository;
        this.siteRepository = siteRepository;
        this.lemmaRepository = lemmaRepository;
    }

    /**
     * Расчитывает абсолютную релевантность страницы как сумму рейтингов лемм,
     * найденных на странице, затем её относительную релевантность.
     */
    public float getRelevanceForPage(Page page, List<Lemma> lemmas) {
        Set<String> lemmaTexts = lemmas.stream()
                .map(Lemma::getLemma)
                .collect(Collectors.toSet());

        List<SearchIndex> searchIndexes = indexRepository.findIndexesForPageAndLemmas(page, lemmaTexts);
        return searchIndexes.stream()
                .map(SearchIndex::getRanking)
                .reduce(0.0f, Float::sum);
    }

    /**
     * Основной метод поиска.
     * Он:
     * 1. Извлекает леммы из поискового запроса.
     * 2. Если задан определённый сайт, ограничивает поиск по нему.
     * 3. Из базы получает объекты Lemma для извлечённых лемм.
     * 4. Сортирует леммы по возрастанию частоты.
     * 5. Итеративно находит пересечение страниц для всех лемм.
     * 6. Рассчитывает релевантность для каждой страницы, формирует сниппет.
     */
    public SearchResponse search(String query, String siteUrl, int offset, int limit) throws IOException {
        if (query == null || query.trim().isEmpty()) {
            return SearchResponse.error("Задан пустой поисковый запрос");
        }

        // Извлечение уникальных лемм из запроса
        Set<String> lemmaTexts = Lemmatizer.extractLemmas(query);
        if (lemmaTexts.isEmpty()) {
            return SearchResponse.error("По запросу не найдено значимых слов");
        }

        // Если указан сайт, ищем его по URL; иначе поиск по всем сайтам
        Site site = (siteUrl != null) ? siteRepository.findSiteByUrl(siteUrl) : null;

        // Получаем леммы из базы по извлечённым текстам
        List<Lemma> lemmas = getLemmasFromDatabase(lemmaTexts, site);
        if (lemmas.isEmpty()) {
            return SearchResponse.error("Не удалось найти леммы для поиска");
        }

        // Сортируем леммы по частоте (от самых редких)
        lemmas.sort(Comparator.comparingInt(Lemma::getFrequency));

        // Итеративно получаем список страниц, на которых встречаются все леммы
        List<Page> matchingPages = null;
        for (int i = 0; i < lemmas.size(); i++) {
            List<Page> pagesForLemma = indexRepository.findPagesByLemma(lemmas.get(i));
            if (i == 0) {
                matchingPages = new ArrayList<>(pagesForLemma);
            } else {
                matchingPages.retainAll(pagesForLemma);
            }
            if (matchingPages.isEmpty()) break;
        }

        if (matchingPages == null || matchingPages.isEmpty()) {
            return SearchResponse.success(0, Collections.emptyList());
        }

        // Вычисляем абсолютную релевантность для каждой страницы
        Map<Page, Float> relevanceMap = new HashMap<>();
        float maxRelevance = 0;
        for (Page page : matchingPages) {
            float relevance = getRelevanceForPage(page, lemmas);
            relevanceMap.put(page, relevance);
            maxRelevance = Math.max(maxRelevance, relevance);
        }

        final float finalMaxRelevance = maxRelevance;
        // Формируем список результатов с вычисленной относительной релевантностью и сниппетом
        List<SearchResponse.SearchResult> results = relevanceMap.entrySet().stream()
                .map(entry -> new SearchResponse.SearchResult(
                        entry.getKey().getSite().getUrl(),
                        entry.getKey().getSite().getName(),
                        entry.getKey().getPath(),
                        createSnippet(entry.getKey().getContent(), lemmaTexts),
                        finalMaxRelevance > 0 ? entry.getValue() / finalMaxRelevance : 0
                ))
                .sorted(Comparator.comparing(SearchResponse.SearchResult::getRelevance).reversed())
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());

        return SearchResponse.success(matchingPages.size(), results);
    }

    /**
     * Формирует сниппет — фрагмент текста страницы с выделенными совпадениями.
     */
    private String createSnippet(String content, Set<String> lemmas) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        String[] words = content.split("\\s+");
        StringBuilder snippet = new StringBuilder();
        int maxSnippetWords = 30;
        int wordCount = 0;
        for (String word : words) {
            String normalizedWord = word.toLowerCase().replaceAll("[^а-яА-Я]", "");
            if (lemmas.contains(normalizedWord)) {
                snippet.append("<b>").append(word).append("</b> ");
            } else {
                snippet.append(word).append(" ");
            }
            if (++wordCount >= maxSnippetWords) break;
        }
        return snippet.toString().trim() + "...";
    }

    /**
     * Извлекает из базы объектов Lemma, соответствующих заданным леммам.
     * Если site задан, ищет только для этого сайта; иначе — для всех.
     */
    private List<Lemma> getLemmasFromDatabase(Set<String> lemmaTexts, Site site) {
        List<Lemma> lemmas = new ArrayList<>();
        for (String lemmaText : lemmaTexts) {
            Optional<Lemma> lemmaOpt = (site != null)
                    ? lemmaRepository.findByLemmaAndSite(lemmaText, site)
                    : lemmaRepository.findByLemma(lemmaText);
            lemmaOpt.ifPresent(lemmas::add);
        }
        return lemmas;
    }
}
