package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import searchengine.config.IndexingState;
import searchengine.dto.response.SearchResponse;
import searchengine.dto.response.SearchResult;
import searchengine.model.*;
import searchengine.repository.*;

import java.util.*;
import java.util.stream.Collectors;

import java.util.concurrent.Semaphore;
@RequiredArgsConstructor
@Service
public class SearchService {
    private final Lemmatizer lemmatizer;

    private static final Logger logger = LoggerFactory.getLogger(SearchService.class);

    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final IndexingState indexingState;

    private final Semaphore databaseSemaphore = new Semaphore(5);
    public SearchResponse search(String query, String siteUrl, int offset, int limit) {

        SearchResponse response = new SearchResponse();
        if (query == null || query.trim().isEmpty()) {
            response.setResult(false);
            response.setError("Задан пустой поисковый запрос");
            return response;
        }

        Site site = null;
        if (siteUrl != null && !siteUrl.isEmpty()) {
            site = siteRepository.findSiteByUrl(siteUrl);
            if (site == null) {
                response.setResult(false);
                response.setError("Сайт не найден");
                return response;
            }
        }

        List<SearchResult> results = new ArrayList<>();

        try {

            databaseSemaphore.acquire();
            if (indexingState.isStopRequested()) {
                response.setResult(false);
                response.setError("Поиск прерван из-за остановки индексации");
                return response;
            }

            if (query == null || query.trim().isEmpty()) {
                response.setResult(false);
                response.setError("Задан пустой поисковый запрос");
                logger.warn("Задан пустой поисковый запрос");
                return response;
            }

            if (siteUrl != null && !siteUrl.isEmpty()) {
                site = siteRepository.findSiteByUrl(siteUrl);
                if (site == null) {
                    response.setResult(false);
                    response.setError("Сайт не найден");
                    logger.warn("Сайт не найден: {}", siteUrl);
                    return response;
                }
                logger.info("Поиск по сайту: {}", site.getUrl());
            } else {
                logger.info("Поиск по всем сайтам");
            }

            Map<String, Integer> lemmasMap = lemmatizer.getLemmas(query);
            List<String> queryLemmas = new ArrayList<>(lemmasMap.keySet());
            if (queryLemmas.isEmpty()) {
                response.setResult(true);
                response.setCount(0);
                response.setData(results);
                logger.warn("Запрос не содержит лемм: {}", query);
                return response;
            }
            logger.info("Леммы из запроса: {}", queryLemmas);

            List<Lemma> filteredLemmas = filterCommonLemmas(site, queryLemmas);
            if (filteredLemmas.isEmpty()) {
                response.setResult(true);
                response.setCount(0);
                response.setData(results);
                logger.warn("Все леммы исключены из-за высокой частоты");
                return response;
            }
            logger.info("Отфильтрованные леммы: {}", filteredLemmas.stream().map(Lemma::getLemma).collect(Collectors.toList()));

            filteredLemmas.sort(Comparator.comparingInt(Lemma::getFrequency));
            logger.info("Леммы после сортировки: {}", filteredLemmas.stream().map(Lemma::getLemma).collect(Collectors.toList()));

            List<Page> pages = findPagesByLemmasAndSite(filteredLemmas, site);
            if (pages.isEmpty()) {
                response.setResult(true);
                response.setCount(0);
                response.setData(results);
                logger.warn("Страницы, содержащие все леммы, не найдены");
                return response;
            }
            logger.info("Найдено страниц: {}", pages.size());

            Map<Page, Double> relevanceMap = calculateRelevance(pages, filteredLemmas);
            logger.info("Релевантность страниц: {}", relevanceMap);

            pages.sort((p1, p2) -> Double.compare(relevanceMap.get(p2), relevanceMap.get(p1)));
            logger.info("Страницы после сортировки по релевантности: {}", pages.stream().map(Page::getPath).collect(Collectors.toList()));

            int totalResults = pages.size();
            int start = Math.min(offset, totalResults);
            int end = Math.min(start + limit, totalResults);
            List<Page> paginatedPages = pages.subList(start, end);

            for (Page page : paginatedPages) {
                SearchResult result = new SearchResult();
                result.setSite(page.getSite().getUrl());
                result.setSiteName(page.getSite().getName());
                result.setUri(page.getPath());
                result.setTitle(Jsoup.parse(page.getContent()).title());
                result.setSnippet(createSnippet(page, queryLemmas));
                result.setRelevance(relevanceMap.get(page));
                results.add(result);
                logger.info("Добавлен результат: {}", result);
            }

            response.setResult(true);
            response.setCount(totalResults);
            response.setData(results);
            logger.info("Результаты поиска успешно сформированы");
        } catch (Exception e) {
            response.setResult(false);
            response.setError("Ошибка поиска: " + e.getMessage());
            logger.error("Ошибка при выполнении поиска: {}", e.getMessage(), e);
        } finally {
            databaseSemaphore.release();
        }

        return response;
    }

    private List<Lemma> filterCommonLemmas(Site site, List<String> lemmas) {
        long totalPages = site != null ? pageRepository.countBySite(site) : pageRepository.count();
        double threshold = 1.0;
        List<Lemma> filteredLemmas = new ArrayList<>();

        logger.info("Общее количество страниц на сайте: {}", totalPages);
        logger.info("Порог для исключения лемм: {}", threshold);

        for (String lemma : lemmas) {
            List<Lemma> lemmaEntities = site != null
                    ? lemmaRepository.findAllByLemmaAndSite(lemma, site)
                    : lemmaRepository.findAllByLemma(lemma);

            for (Lemma lemmaObj : lemmaEntities) {
                double lemmaFrequencyRatio = (double) lemmaObj.getFrequency() / totalPages;
                logger.info("Лемма: {}, Частота: {}, Отношение частоты: {}", lemmaObj.getLemma(), lemmaObj.getFrequency(), lemmaFrequencyRatio);
                if (lemmaFrequencyRatio <= threshold) {
                    filteredLemmas.add(lemmaObj);
                    logger.info("Лемма добавлена: {}", lemmaObj.getLemma());
                } else {
                    logger.info("Лемма исключена из-за высокой частоты: {}", lemmaObj.getLemma());
                }
            }
        }

        logger.info("Отфильтрованные леммы: {}", filteredLemmas.stream()
                .map(Lemma::getLemma)
                .collect(Collectors.toList()));

        return filteredLemmas;
    }

    private List<Page> findPagesByLemmasAndSite(List<Lemma> lemmas, Site site) {
        if (site == null) {

            return indexRepository.findPagesByLemmas(lemmas.stream()
                    .map(Lemma::getLemma)
                    .collect(Collectors.toList()));
        } else {

            return indexRepository.findPagesByLemmasAndSite(lemmas.stream()
                    .map(Lemma::getLemma)
                    .collect(Collectors.toList()), site);
        }
    }

    private Map<Page, Double> calculateRelevance(List<Page> pages, List<Lemma> lemmas) {
        Map<Page, Double> relevanceMap = new HashMap<>();
        double maxRelevance = 0;

        for (Page page : pages) {
            double relevance = 0;
            for (Lemma lemma : lemmas) {
                Float rank = indexRepository.findRankByPageAndLemma(page, lemma);
                if (rank != null) {
                    relevance += rank;
                }
            }
            relevanceMap.put(page, relevance);
            if (relevance > maxRelevance) {
                maxRelevance = relevance;
            }
        }

        if (maxRelevance > 0) {
            for (Map.Entry<Page, Double> entry : relevanceMap.entrySet()) {
                entry.setValue(entry.getValue() / maxRelevance);
            }
        }

        return relevanceMap;
    }
    private String createSnippet(Page page, List<String> queryLemmas) {
        String content = Jsoup.parse(page.getContent()).text();
        List<WordInfo> foundWords = new ArrayList<>();
        Map<String, Set<String>> lemmaFormsMap = new HashMap<>();

        queryLemmas.forEach(lemma -> lemmaFormsMap.put(lemma, new HashSet<>()));

        String[] words = content.split("\\s+");
        for (int i = 0; i < words.length; i++) {
            String word = cleanWord(words[i]);
            if (word.isEmpty()) continue;

            List<String> wordLemmas = lemmatizer.getWordLemmas(word);
            for (String lemma : wordLemmas) {
                if (lemmaFormsMap.containsKey(lemma)) {
                    lemmaFormsMap.get(lemma).add(word.toLowerCase());
                    foundWords.add(new WordInfo(i, word, lemma));
                }
            }
        }

        foundWords.sort(Comparator.comparingInt(w -> w.position));

        return buildSnippet(words, foundWords, lemmaFormsMap);
    }

    private String buildSnippet(String[] words, List<WordInfo> foundWords, Map<String, Set<String>> lemmaFormsMap) {
        StringBuilder snippet = new StringBuilder();
        int lastAddedPos = -2;
        int snippetLength = 0;
        final int MAX_SNIPPET_LENGTH = 300;

        for (WordInfo wordInfo : foundWords) {
            if (snippetLength >= MAX_SNIPPET_LENGTH) break;
            if (wordInfo.position <= lastAddedPos) continue;

            int start = Math.max(0, wordInfo.position - 5);
            int end = Math.min(words.length, wordInfo.position + 5);
            lastAddedPos = end;

            StringBuilder fragment = new StringBuilder();
            for (int i = start; i < end; i++) {
                String word = words[i];
                String cleanWord = cleanWord(word).toLowerCase();

                boolean isMatch = lemmaFormsMap.values().stream()
                        .anyMatch(forms -> forms.contains(cleanWord));

                fragment.append(isMatch ? "<b>" + word + "</b>" : word)
                        .append(" ");
            }

            String fragText = fragment.toString().trim() + "... ";
            if (snippetLength + fragText.length() > MAX_SNIPPET_LENGTH) {
                fragText = fragText.substring(0, MAX_SNIPPET_LENGTH - snippetLength) + "... ";
            }

            snippet.append(fragText);
            snippetLength += fragText.length();
        }

        return snippet.toString().trim();
    }

    private String cleanWord(String word) {
        return word.replaceAll("[^\\p{L}\\d]", "").trim();
    }

    private static class WordInfo {
        int position;
        String originalWord;
        String lemma;

        WordInfo(int position, String originalWord, String lemma) {
            this.position = position;
            this.originalWord = originalWord;
            this.lemma = lemma;
        }
    }
}