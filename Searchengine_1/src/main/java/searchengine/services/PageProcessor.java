package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import searchengine.config.IndexingState;
import searchengine.model.*;
import searchengine.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.transaction.Transactional;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@RequiredArgsConstructor
@Service
public class PageProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PageProcessor.class);

    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final SiteRepository siteRepository;
    private final TopicRepository topicRepository; // ДОБАВЛЕНО
    private final Lemmatizer lemmatizer;
    private final TopicExtractorService topicExtractorService; // ДОБАВЛЕНО
    private final IndexingState indexingState;
    private final TopicGroupingService topicGroupingService; // ДОБАВЛЕНО
    private final TopicGroupRepository topicGroupRepository; // ДОБАВЛЕНО (опционально)

    private final AtomicBoolean isIndexingStopped = new AtomicBoolean(false);

    /**
     * Метод для индексации отдельной страницы
     *
     * @param site  Сайт
     * @param url   URL страницы
     * @param depth Глубина рекурсии
     */
    public void indexPage(Site site, String url, int depth) throws IOException, InterruptedException {
        if (isIndexingStopped.get()) {
            throw new InterruptedException("Индексация остановлена");
        }

        try {
            String path = url.replace(site.getUrl(), "");
            if (path.isEmpty()) path = "/";

            Optional<Page> existingPage = pageRepository.findBySiteAndPath(site.getId(), path);
            existingPage.ifPresent(this::deletePageInfo);

            if (pageRepository.existsBySiteAndPath(site, url.replace(site.getUrl(), ""))) {
                logger.warn("Страница уже существует: {}", url);
                return;
            }

            Connection connection = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .referrer("https://www.google.com")
                    .ignoreContentType(true);

            Connection.Response response = connection.execute();
            int statusCode = response.statusCode();

            if (statusCode >= 400) {
                logger.warn("Ошибка HTTP {}: {}", statusCode, url);
                return;
            }

            String contentType = response.contentType();
            if (contentType == null || !(contentType.startsWith("text/") || contentType.contains("xml"))) {
                logger.warn("Неподдерживаемый тип содержимого: {}", contentType);
                return;
            }

            Document doc = response.parse();
            Page page = savePage(site, url, doc);

            // Индексируем содержание страницы
            indexPageContent(site, page);

            // Извлекаем и сохраняем темы из страницы (НОВОЕ)
            extractAndSaveTopics(page, doc.html());

            // Обновляем счетчик тем на странице
            updatePageTopicCount(page);

            // Обрабатываем ссылки (только если это рекурсивная индексация)
            if (depth < 5) { // Ограничиваем глубину рекурсии
                processLinks(site, doc, depth + 1);
            }

            logger.info("Страница успешно проиндексирована: {} (тем: {})",
                    url, page.getTopicCount());

        } catch (Exception e) {
            logger.error("Ошибка при индексации страницы {}: {}", url, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Извлекает и сохраняет темы из страницы
     *
     * @param page Страница
     * @param htmlContent HTML-контент страницы
     */
    // В классе PageProcessor, в методе extractAndSaveTopics замените этот код:

    @Transactional
    private void extractAndSaveTopics(Page page, String htmlContent) {
        try {
            // Удаляем старые темы для этой страницы
            topicRepository.deleteByPageId(page.getId());

            // Извлекаем темы
            List<Topic> topics = topicExtractorService.extractTopics(page, htmlContent);

            if (!topics.isEmpty()) {
                // Сохраняем каждую тему
                for (Topic topic : topics) {
                    // Устанавливаем связи
                    topic.setPage(page);
                    topic.setSite(page.getSite());

                    // Подсчитываем количество лемм в теме
                    topic.setLemmaCount(calculateLemmaCount(topic.getContent()));

                    // Сохраняем тему
                    Topic savedTopic = topicRepository.save(topic);

                    // Группируем тему (УПРОЩЕННАЯ ВЕРСИЯ)
                    try {
                        // Отложенная группировка - просто сохраняем, группировать будем отдельно
                        // topicGroupingService.assignToTopicGroup(savedTopic);
                    } catch (Exception e) {
                        logger.warn("Ошибка при группировке темы: {}", e.getMessage());
                    }
                }

                logger.debug("Извлечено {} тем для страницы: {}", topics.size(), page.getPath());
            } else {
                logger.debug("Темы не найдены на странице: {}", page.getPath());
            }

        } catch (Exception e) {
            logger.error("Ошибка при извлечении тем для страницы {}: {}",
                    page.getPath(), e.getMessage(), e);
        }
    }
        /**
     * Подсчитывает количество лемм в тексте
     *
     * @param text Текст для анализа
     * @return Количество лемм
     */
    private int calculateLemmaCount(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }

        try {
            Map<String, Integer> lemmas = lemmatizer.extractLemmasWithRank(text);
            return lemmas.values().stream()
                    .mapToInt(Integer::intValue)
                    .sum();
        } catch (Exception e) {
            logger.warn("Ошибка при подсчете лемм в теме: {}", e.getMessage());
            // Возвращаем примерное количество слов как fallback
            return text.trim().split("\\s+").length;
        }
    }

    /**
     * Обновляет счетчик тем на странице
     *
     * @param page Страница
     */
    @Transactional
    private void updatePageTopicCount(Page page) {
        try {
            long topicCount = topicRepository.countByPageId(page.getId());
            page.setTopicCount((int) topicCount);
            pageRepository.save(page);

            logger.debug("Обновлен счетчик тем для страницы {}: {}", page.getPath(), topicCount);
        } catch (Exception e) {
            logger.warn("Ошибка при обновлении счетчика тем для страницы {}: {}",
                    page.getPath(), e.getMessage());
        }
    }

    /**
     * Метод для обработки ссылок на странице
     *
     * @param site  Сайт
     * @param doc   HTML-документ
     * @param depth Глубина рекурсии
     */
    private void processLinks(Site site, Document doc, int depth) throws InterruptedException, IOException {
        Elements links = doc.select("a[href]");
        int processedLinks = 0;
        int maxLinksPerPage = 50; // Ограничиваем количество ссылок на странице

        for (Element link : links) {
            if (isIndexingStopped.get() || indexingState.isStopRequested()) {
                throw new InterruptedException("Индексация остановлена");
            }

            if (processedLinks >= maxLinksPerPage) {
                logger.debug("Достигнут лимит ссылок для страницы: {}", maxLinksPerPage);
                break;
            }

            String nextUrl = link.absUrl("href");
            if (isValidLinkForIndexing(site, nextUrl)) {
                try {
                    // Небольшая задержка для предотвращения DDoS
                    Thread.sleep(100);

                    indexPage(site, nextUrl, depth);
                    processedLinks++;

                } catch (Exception e) {
                    logger.warn("Ошибка при обработке ссылки {}: {}", nextUrl, e.getMessage());
                }
            }
        }

        logger.debug("Обработано {} ссылок со страницы", processedLinks);
    }

    /**
     * Проверяет, является ли ссылка валидной для индексации
     *
     * @param site Сайт
     * @param url  URL для проверки
     * @return true если ссылка валидна
     */
    private boolean isValidLinkForIndexing(Site site, String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }

        // Проверяем, что ссылка принадлежит текущему сайту
        if (!url.startsWith(site.getUrl())) {
            return false;
        }

        // Исключаем якорные ссылки
        if (url.contains("#")) {
            return false;
        }

        // Исключаем файлы
        String lowerUrl = url.toLowerCase();
        if (lowerUrl.matches(".*\\.(jpg|jpeg|png|gif|bmp|pdf|doc|docx|xls|xlsx|ppt|pptx|zip|rar|7z|exe|dmg|mp3|mp4|avi|mov)$")) {
            return false;
        }

        // Исключаем почтовые ссылки
        if (url.startsWith("mailto:")) {
            return false;
        }

        // Исключаем javascript ссылки
        if (url.startsWith("javascript:")) {
            return false;
        }

        // Проверяем, не индексировали ли мы уже эту страницу
        String path = url.replace(site.getUrl(), "");
        if (path.isEmpty()) path = "/";

        if (pageRepository.existsBySiteAndPath(site, path)) {
            return false;
        }

        return true;
    }

    /**
     * Метод для сохранения страницы в базу данных
     *
     * @param site Сайт
     * @param url  URL страницы
     * @param doc  HTML-документ
     * @return Сохраненная страница
     */
    @Transactional
    private Page savePage(Site site, String url, Document doc) {
        Page page = new Page();
        page.setSite(site);
        String path = url.replace(site.getUrl(), "");
        if (path.isEmpty()) {
            path = "/";
        }
        page.setPath(path);
        page.setCode(doc.connection().response().statusCode());
        page.setContent(doc.html());
        page.setTopicCount(0); // Инициализируем счетчик

        // Обновляем время статуса сайта
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);

        return pageRepository.save(page);
    }

    /**
     * Метод для индексации содержимого страницы
     *
     * @param site Сайт
     * @param page Страница
     */
    @Transactional
    private void indexPageContent(Site site, Page page) {
        if (isIndexingStopped.get() || indexingState.isStopRequested()) {
            return;
        }

        try {
            // Извлекаем текст из HTML
            String text = Jsoup.parse(page.getContent()).text();

            // Извлекаем леммы с рангами
            Map<String, Integer> lemmas = lemmatizer.extractLemmasWithRank(text);

            List<Lemma> lemmaList = new ArrayList<>();
            List<SearchIndex> indexList = new ArrayList<>();

            // Обрабатываем каждую лемму
            lemmas.forEach((lemmaText, rank) -> {
                if (isIndexingStopped.get() || indexingState.isStopRequested()) {
                    return;
                }

                Lemma lemma = findOrCreateLemma(site, lemmaText);
                lemma.setFrequency(lemma.getFrequency() + 1);
                lemmaList.add(lemma);

                SearchIndex index = new SearchIndex();
                index.setPage(page);
                index.setLemma(lemma);
                index.setRanking(rank);
                indexList.add(index);
            });

            // Сохраняем леммы и индексы
            if (!lemmaList.isEmpty()) {
                lemmaRepository.saveAll(lemmaList);
            }

            if (!indexList.isEmpty()) {
                indexRepository.saveAll(indexList);
            }

            logger.debug("Индексировано {} лемм для страницы: {}", lemmas.size(), page.getPath());

        } catch (Exception e) {
            logger.error("Ошибка при индексации содержимого страницы {}: {}",
                    page.getPath(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Поиск или создание леммы
     *
     * @param site      Сайт
     * @param lemmaText Текст леммы
     * @return Лемма
     */
    @Transactional
    private Lemma findOrCreateLemma(Site site, String lemmaText) {
        return lemmaRepository.findByLemmaAndSiteId(lemmaText, site.getId())
                .orElseGet(() -> {
                    Lemma newLemma = new Lemma();
                    newLemma.setSite(site);
                    newLemma.setLemma(lemmaText);
                    newLemma.setFrequency(1);
                    return lemmaRepository.save(newLemma);
                });
    }

    /**
     * Удаляет информацию о странице из таблиц page, lemma, index и topic
     *
     * @param page Страница
     */
    @Transactional
    public void deletePageInfo(Page page) {
        logger.info("Удаление информации о странице: {}", page.getPath());

        try {
            // 1. Удаляем связанные темы
            topicRepository.deleteByPageId(page.getId());
            logger.debug("Удалены темы для страницы: {}", page.getPath());

            // 2. Удаляем индексы и обновляем леммы
            List<SearchIndex> indexes = indexRepository.findByPage(page);
            for (SearchIndex index : indexes) {
                Lemma lemma = index.getLemma();
                lemma.setFrequency(Math.max(0, lemma.getFrequency() - 1));

                if (lemma.getFrequency() == 0) {
                    lemmaRepository.delete(lemma);
                } else {
                    lemmaRepository.save(lemma);
                }

                indexRepository.delete(index);
            }

            // 3. Удаляем саму страницу
            pageRepository.delete(page);

            logger.info("Информация о странице полностью удалена: {}", page.getPath());

        } catch (Exception e) {
            logger.error("Ошибка при удалении информации о странице {}: {}",
                    page.getPath(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Удаляет информацию о странице, если она существует
     *
     * @param site Сайт
     * @param url  URL страницы
     */
    @Transactional
    public void deletePageInfoIfExists(Site site, String url) {
        String path = url.replace(site.getUrl(), "");
        if (path.isEmpty()) path = "/";

        Optional<Page> existingPage = pageRepository.findBySiteAndPath(site.getId(), path);
        if (existingPage.isPresent()) {
            deletePageInfo(existingPage.get());
            logger.info("Удалена существующая страница перед переиндексацией: {}", url);
        } else {
            logger.debug("Страница не найдена для удаления: {}", url);
        }
    }

    /**
     * Метод для индексации отдельной страницы без рекурсии (для API)
     *
     * @param site Сайт
     * @param url  URL страницы
     */
    public void indexSinglePage(Site site, String url) throws IOException, InterruptedException {
        logger.info("Индексация отдельной страницы: {}", url);

        // Останавливаем рекурсивную индексацию на глубине 0
        indexPage(site, url, 0);
    }

    /**
     * Получает статистику по темам для страницы
     *
     * @param pageId ID страницы
     * @return Статистика по темам
     */
    public Map<String, Object> getPageTopicsStatistics(int pageId) {
        Map<String, Object> stats = new HashMap<>();

        try {
            long topicCount = topicRepository.countByPageId(pageId);
            List<Topic> topics = topicRepository.findByPageId(pageId);

            int totalLemmaCount = topics.stream()
                    .mapToInt(Topic::getLemmaCount)
                    .sum();

            stats.put("topicCount", topicCount);
            stats.put("totalLemmaCount", totalLemmaCount);
            stats.put("topics", topics);

            // Находим самую частую тему (по количеству лемм)
            topics.stream()
                    .max(Comparator.comparingInt(Topic::getLemmaCount))
                    .ifPresent(topTopic -> {
                        stats.put("mostFrequentTopic", topTopic.getTitle());
                        stats.put("mostFrequentTopicLemmaCount", topTopic.getLemmaCount());
                    });

        } catch (Exception e) {
            logger.error("Ошибка при получении статистики тем для страницы {}: {}",
                    pageId, e.getMessage());
            stats.put("error", e.getMessage());
        }

        return stats;
    }

    /**
     * Останавливает процесс индексации
     */
    public void stopIndexing() {
        isIndexingStopped.set(true);
        logger.info("Процесс индексации остановлен");
    }

    /**
     * Возобновляет процесс индексации
     */
    public void resumeIndexing() {
        isIndexingStopped.set(false);
        logger.info("Процесс индексации возобновлен");
    }

    /**
     * Проверяет, остановлена ли индексация
     *
     * @return true если индексация остановлена
     */
    public boolean isIndexingStopped() {
        return isIndexingStopped.get();
    }
}