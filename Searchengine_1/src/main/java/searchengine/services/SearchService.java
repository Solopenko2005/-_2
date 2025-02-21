package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.dto.response.SearchResponse;
import searchengine.dto.response.SearchResult;
import searchengine.model.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private static final Logger logger = LoggerFactory.getLogger(SearchService.class);

    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final Lemmatizer lemmatizer;

    @Autowired
    public SearchService(LemmaRepository lemmaRepository, IndexRepository indexRepository,
                         PageRepository pageRepository, SiteRepository siteRepository, Lemmatizer lemmatizer) {
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
        this.lemmatizer = lemmatizer;
    }

    public SearchResponse search(String query, String siteUrl, int offset, int limit) {
        SearchResponse response = new SearchResponse();
        List<SearchResult> results = new ArrayList<>();

        try {
            // Проверка на пустой запрос
            if (query == null || query.trim().isEmpty()) {
                response.setResult(false);
                response.setError("Задан пустой поисковый запрос");
                logger.warn("Задан пустой поисковый запрос");
                return response;
            }

            Site site = null;
            if (siteUrl != null && !siteUrl.isEmpty()) {
                site = siteRepository.findByUrl(siteUrl).orElse(null);
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

            // Разбиваем запрос на леммы
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

            // Исключаем леммы с высокой частотой
            List<Lemma> filteredLemmas = filterCommonLemmas(site, queryLemmas);
            if (filteredLemmas.isEmpty()) {
                response.setResult(true);
                response.setCount(0);
                response.setData(results);
                logger.warn("Все леммы исключены из-за высокой частоты");
                return response;
            }
            logger.info("Отфильтрованные леммы: {}", filteredLemmas.stream().map(Lemma::getLemma).collect(Collectors.toList()));

            // Сортируем леммы по частоте (от самых редких к самым частым)
            filteredLemmas.sort(Comparator.comparingInt(Lemma::getFrequency));
            logger.info("Леммы после сортировки: {}", filteredLemmas.stream().map(Lemma::getLemma).collect(Collectors.toList()));

            // Находим страницы, содержащие все леммы
            List<Page> pages = findPagesByLemmas(site, filteredLemmas);
            if (pages.isEmpty()) {
                response.setResult(true);
                response.setCount(0);
                response.setData(results);
                logger.warn("Страницы, содержащие все леммы, не найдены");
                return response;
            }
            logger.info("Найдено страниц: {}", pages.size());

            // Рассчитываем релевантность
            Map<Page, Double> relevanceMap = calculateRelevance(pages, filteredLemmas);
            logger.info("Релевантность страниц: {}", relevanceMap);

            // Сортируем страницы по релевантности (от большей к меньшей)
            pages.sort((p1, p2) -> Double.compare(relevanceMap.get(p2), relevanceMap.get(p1)));
            logger.info("Страницы после сортировки по релевантности: {}", pages.stream().map(Page::getPath).collect(Collectors.toList()));

            // Применяем пагинацию (offset и limit)
            int totalResults = pages.size();
            int start = Math.min(offset, totalResults);
            int end = Math.min(start + limit, totalResults);
            List<Page> paginatedPages = pages.subList(start, end);

            // Формируем результаты
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
        }

        return response;
    }

    private List<Lemma> filterCommonLemmas(Site site, List<String> lemmas) {
        // Получаем общее количество страниц на сайте
        long totalPages = site != null ? pageRepository.countBySite(site) : pageRepository.count();
        double threshold = 0.99; // Порог для исключения лемм (99%)
        List<Lemma> filteredLemmas = new ArrayList<>();

        // Логируем общее количество страниц и порог
        logger.info("Общее количество страниц на сайте: {}", totalPages);
        logger.info("Порог для исключения лемм: {}", threshold);

        // Если сайт содержит мало страниц (например, меньше 10), отключаем фильтрацию
        if (totalPages < 200) {
            logger.info("Сайт содержит мало страниц ({}), фильтрация лемм отключена", totalPages);
            for (String lemma : lemmas) {
                List<Lemma> lemmaEntities = site != null
                        ? lemmaRepository.findByLemmaAndSite(lemma, site)
                        : lemmaRepository.findByLemma(lemma);
                if (!lemmaEntities.isEmpty()) {
                    Lemma lemmaEntity = lemmaEntities.get(0);
                    filteredLemmas.add(lemmaEntity);
                    logger.info("Лемма добавлена (фильтрация отключена): {}", lemmaEntity.getLemma());
                } else {
                    logger.info("Лемма не найдена в базе данных: {}", lemma);
                }
            }
            return filteredLemmas;
        }

        // Фильтрация для сайтов с большим количеством страниц
        for (String lemma : lemmas) {
            List<Lemma> lemmaEntities = site != null
                    ? lemmaRepository.findByLemmaAndSite(lemma, site)
                    : lemmaRepository.findByLemma(lemma);
            if (!lemmaEntities.isEmpty()) {
                Lemma lemmaEntity = lemmaEntities.get(0);
                double lemmaFrequencyRatio = (double) lemmaEntity.getFrequency() / totalPages;

                // Логируем информацию о лемме
                logger.info("Лемма: {}, Частота: {}, Отношение частоты: {}", lemmaEntity.getLemma(), lemmaEntity.getFrequency(), lemmaFrequencyRatio);

                // Проверяем, превышает ли отношение частоты порог
                if (lemmaFrequencyRatio <= threshold) {
                    filteredLemmas.add(lemmaEntity);
                    logger.info("Лемма добавлена: {}", lemmaEntity.getLemma());
                } else {
                    logger.info("Лемма исключена из-за высокой частоты: {}", lemmaEntity.getLemma());
                }
            } else {
                logger.info("Лемма не найдена в базе данных: {}", lemma);
            }
        }

        // Логируем итоговый список отфильтрованных лемм
        logger.info("Отфильтрованные леммы: {}", filteredLemmas.stream()
                .map(Lemma::getLemma)
                .collect(Collectors.toList()));

        return filteredLemmas;
    }

    private List<Page> findPagesByLemmas(Site site, List<Lemma> lemmas) {
        List<Page> pages = new ArrayList<>();
        if (lemmas.isEmpty()) {
            logger.warn("Список лемм пуст");
            return pages;
        }

        // Начинаем с первой леммы
        Lemma firstLemma = lemmas.get(0);
        List<Page> initialPages = site != null
                ? indexRepository.findPagesByLemmaAndSite(site, firstLemma)
                : indexRepository.findPagesByLemma(firstLemma);

        logger.info("Найдено страниц для первой леммы: {}", initialPages.size());

        for (Page page : initialPages) {
            boolean containsAllLemmas = true;
            for (Lemma lemma : lemmas) {
                if (!indexRepository.existsByPageAndLemma(page, lemma)) {
                    containsAllLemmas = false;
                    logger.debug("Страница {} не содержит лемму {}", page.getPath(), lemma.getLemma());
                    break;
                }
            }
            if (containsAllLemmas) {
                pages.add(page);
                logger.debug("Страница добавлена: {}", page.getPath());
            }
        }

        return pages;
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
                } else {
                    logger.debug("Ранг не найден для страницы {} и леммы {}", page.getPath(), lemma.getLemma());
                }
            }
            relevanceMap.put(page, relevance);
            if (relevance > maxRelevance) {
                maxRelevance = relevance;
            }
            logger.debug("Релевантность страницы {}: {}", page.getPath(), relevance);
        }

        // Нормализация релевантности
        if (maxRelevance > 0) {
            for (Map.Entry<Page, Double> entry : relevanceMap.entrySet()) {
                entry.setValue(entry.getValue() / maxRelevance);
                logger.debug("Нормализованная релевантность страницы {}: {}", entry.getKey().getPath(), entry.getValue());
            }
        } else {
            logger.warn("Максимальная релевантность равна нулю. Нормализация невозможна.");
        }

        return relevanceMap;
    }

    private String createSnippet(Page page, List<String> lemmas) {
        String content = Jsoup.parse(page.getContent()).text();
        StringBuilder snippet = new StringBuilder();

        for (String lemma : lemmas) {
            int index = content.toLowerCase().indexOf(lemma.toLowerCase());
            if (index != -1) {
                int start = Math.max(0, index - 50);
                int end = Math.min(content.length(), index + 50);
                String fragment = content.substring(start, end);
                fragment = fragment.replaceAll("(?i)" + lemma, "<b>" + lemma + "</b>");
                snippet.append(fragment).append("... ");
                logger.debug("Сниппет для леммы {}: {}", lemma, fragment);
            } else {
                logger.debug("Лемма {} не найдена в тексте страницы", lemma);
            }
        }

        return snippet.toString();
    }

}