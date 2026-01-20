package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Topic;
import searchengine.model.TopicGroup;
import searchengine.repository.TopicGroupRepository;
import searchengine.repository.TopicRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TopicGroupingService {

    private static final Logger logger = LoggerFactory.getLogger(TopicGroupingService.class);

    private final TopicRepository topicRepository;
    private final TopicGroupRepository topicGroupRepository;
    private final Lemmatizer lemmatizer;


    private final int MIN_LEMMA_MATCH = 3; // Уменьшил до 3 для большей гибкости

    /**
     * Простой метод для группировки всех тем
     */
    @Transactional
    public void groupAllTopics() {
        logger.info("Начинаем группировку тем...");

        try {
            // 1. Очищаем существующие группы
            topicGroupRepository.deleteAll();
            logger.info("Существующие группы удалены");

            // 2. Получаем все темы
            List<Topic> allTopics = topicRepository.findAll();
            logger.info("Всего тем для обработки: {}", allTopics.size());

            if (allTopics.isEmpty()) {
                logger.warn("Нет тем для группировки");
                return;
            }

            // 3. Группируем по простому алгоритму (по схожести заголовков)
            Map<String, List<Topic>> groupedBySimilarTitle = new HashMap<>();

            for (Topic topic : allTopics) {
                String normalizedTitle = normalizeTitle(topic.getTitle());

                boolean added = false;
                for (String existingTitle : groupedBySimilarTitle.keySet()) {
                    if (areTitlesSimilar(normalizedTitle, existingTitle)) {
                        groupedBySimilarTitle.get(existingTitle).add(topic);
                        added = true;
                        break;
                    }
                }

                if (!added) {
                    List<Topic> newGroup = new ArrayList<>();
                    newGroup.add(topic);
                    groupedBySimilarTitle.put(normalizedTitle, newGroup);
                }
            }

            logger.info("Создано {} групп", groupedBySimilarTitle.size());

            // 4. Сохраняем группы в БД
            int groupNum = 0;
            for (Map.Entry<String, List<Topic>> entry : groupedBySimilarTitle.entrySet()) {
                if (entry.getValue().size() > 1) { // Сохраняем только группы с более чем 1 темой
                    createTopicGroup(entry.getKey(), entry.getValue());
                    groupNum++;

                    if (groupNum % 100 == 0) {
                        logger.info("Создано групп: {}", groupNum);
                    }
                }
            }

            logger.info("Группировка завершена. Создано групп: {}", groupNum);

        } catch (Exception e) {
            logger.error("Ошибка при группировке тем: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Нормализация заголовка
     */
    private String normalizeTitle(String title) {
        if (title == null) return "";

        return title.toLowerCase()
                .replaceAll("[^a-zа-яё0-9\\s]", " ") // Удаляем спецсимволы
                .replaceAll("\\s+", " ") // Убираем лишние пробелы
                .trim();
    }

    /**
     * Проверка схожести заголовков
     */
    private boolean areTitlesSimilar(String title1, String title2) {
        if (title1.isEmpty() || title2.isEmpty()) return false;

        // Если заголовки полностью совпадают
        if (title1.equals(title2)) return true;

        // Если один заголовок содержит другой
        if (title1.contains(title2) || title2.contains(title1)) {
            return true;
        }

        // Разбиваем на слова
        String[] words1 = title1.split("\\s+");
        String[] words2 = title2.split("\\s+");

        // Считаем общие слова
        Set<String> set1 = new HashSet<>(Arrays.asList(words1));
        Set<String> set2 = new HashSet<>(Arrays.asList(words2));
        set1.retainAll(set2);

        // Если есть хотя бы 2 общих слова
        return set1.size() >= 2;
    }

    /**
     * Создание группы тем
     */
    @Transactional
    private void createTopicGroup(String groupTitle, List<Topic> topics) {
        try {
            TopicGroup group = new TopicGroup();
            group.setTitle(groupTitle);

            // Создаем краткое описание из первого топика
            if (!topics.isEmpty()) {
                Topic firstTopic = topics.get(0);
                String content = firstTopic.getContent();
                group.setContentSummary(content.length() > 200 ?
                        content.substring(0, 200) + "..." : content);
            }

            group.setFrequency(topics.size());

            // Считаем уникальные сайты
            long siteCount = topics.stream()
                    .map(topic -> topic.getSite().getId())
                    .distinct()
                    .count();
            group.setSiteCount((int) siteCount);

            // Извлекаем ключевые слова из всех тем
            Set<String> keyWords = extractKeyWordsFromTopics(topics);
            group.setKeyLemmas(keyWords);

            // Сохраняем группу
            TopicGroup savedGroup = topicGroupRepository.save(group);

            // Обновляем темы, связывая их с группой
            for (Topic topic : topics) {
                topic.setTopicGroup(savedGroup);
                topicRepository.save(topic);
            }

        } catch (Exception e) {
            logger.error("Ошибка при создании группы '{}': {}", groupTitle, e.getMessage());
        }
    }

    /**
     * Извлечение ключевых слов из списка тем
     */
    private Set<String> extractKeyWordsFromTopics(List<Topic> topics) {
        Set<String> allKeywords = new HashSet<>();

        for (Topic topic : topics) {
            try {
                String text = topic.getTitle() + " " + topic.getContent();
                Map<String, Integer> lemmas = lemmatizer.extractLemmasWithRank(text);

                // Берем топ-5 лемм по частоте
                Set<String> topicKeywords = lemmas.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .limit(5)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toSet());

                allKeywords.addAll(topicKeywords);

            } catch (Exception e) {
                logger.warn("Ошибка при извлечении ключевых слов для темы {}: {}",
                        topic.getId(), e.getMessage());
            }
        }

        // Ограничиваем количество ключевых слов
        return allKeywords.stream()
                .limit(10)
                .collect(Collectors.toSet());
    }

    /**
     * Ручная группировка одной темы
     */
    @Transactional
    public void assignToTopicGroup(Topic topic) {
        try {
            // Ищем похожие группы по заголовку
            String normalizedTitle = normalizeTitle(topic.getTitle());
            List<TopicGroup> existingGroups = topicGroupRepository.findAll();

            TopicGroup bestGroup = null;
            int maxSimilarity = 0;

            for (TopicGroup group : existingGroups) {
                int similarity = calculateSimilarity(normalizedTitle, group.getTitle());
                if (similarity >= 2 && similarity > maxSimilarity) { // Минимум 2 общих слова
                    maxSimilarity = similarity;
                    bestGroup = group;
                }
            }

            if (bestGroup != null) {
                // Добавляем в существующую группу
                topic.setTopicGroup(bestGroup);
                bestGroup.setFrequency(bestGroup.getFrequency() + 1);

                // Обновляем счетчик сайтов
                long siteCount = topicRepository.findByTopicGroup(bestGroup).stream()
                        .map(t -> t.getSite().getId())
                        .distinct()
                        .count();
                bestGroup.setSiteCount((int) siteCount);

                topicRepository.save(topic);
                topicGroupRepository.save(bestGroup);

                logger.debug("Тема {} добавлена в группу {}", topic.getId(), bestGroup.getId());
            } else {
                // Создаем новую группу
                createTopicGroup(topic.getTitle(), Arrays.asList(topic));
            }

        } catch (Exception e) {
            logger.error("Ошибка при группировке темы {}: {}", topic.getId(), e.getMessage());
        }
    }

    /**
     * Расчет схожести заголовков
     */
    private int calculateSimilarity(String title1, String title2) {
        if (title1 == null || title2 == null) return 0;

        String normalized1 = normalizeTitle(title1);
        String normalized2 = normalizeTitle(title2);

        if (normalized1.isEmpty() || normalized2.isEmpty()) return 0;

        // Разбиваем на слова
        Set<String> words1 = new HashSet<>(Arrays.asList(normalized1.split("\\s+")));
        Set<String> words2 = new HashSet<>(Arrays.asList(normalized2.split("\\s+")));

        // Находим пересечение
        words1.retainAll(words2);

        return words1.size();
    }

    /**
     * Получить популярные группы тем
     */
    public List<TopicGroup> getPopularTopicGroups(int limit) {
        return topicGroupRepository.findAll().stream()
                .sorted((g1, g2) -> {
                    int freqCompare = Integer.compare(g2.getFrequency(), g1.getFrequency());
                    if (freqCompare != 0) return freqCompare;
                    return Integer.compare(g2.getSiteCount(), g1.getSiteCount());
                })
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Получить статистику по группам
     */
    public Map<String, Object> getGroupStatistics() {
        Map<String, Object> stats = new HashMap<>();

        List<TopicGroup> allGroups = topicGroupRepository.findAll();
        List<TopicGroup> groupsWithMultipleTopics = allGroups.stream()
                .filter(g -> g.getFrequency() > 1)
                .collect(Collectors.toList());

        stats.put("totalGroups", allGroups.size());
        stats.put("groupsWithMultipleTopics", groupsWithMultipleTopics.size());
        stats.put("totalTopicsInGroups", allGroups.stream()
                .mapToInt(TopicGroup::getFrequency)
                .sum());

        // Топ-10 самых частых тем
        List<Map<String, Object>> topGroups = allGroups.stream()
                .sorted((g1, g2) -> Integer.compare(g2.getFrequency(), g1.getFrequency()))
                .limit(10)
                .map(g -> {
                    Map<String, Object> groupInfo = new HashMap<>();
                    groupInfo.put("id", g.getId());
                    groupInfo.put("title", g.getTitle());
                    groupInfo.put("frequency", g.getFrequency());
                    groupInfo.put("siteCount", g.getSiteCount());
                    return groupInfo;
                })
                .collect(Collectors.toList());

        stats.put("topGroups", topGroups);

        return stats;
    }
    /**
     * Получить группу тем по ID
     */
    public TopicGroup getTopicGroupById(int id) {
        return topicGroupRepository.findById(id).orElse(null);
    }

    /**
     * Поиск групп тем по ключевому слову
     */
    public List<TopicGroup> searchTopicGroups(String query, int limit) {
        String searchQuery = "%" + query.toLowerCase() + "%";

        return topicGroupRepository.findAll().stream()
                .filter(group ->
                        (group.getTitle() != null && group.getTitle().toLowerCase().contains(query.toLowerCase())) ||
                                (group.getContentSummary() != null && group.getContentSummary().toLowerCase().contains(query.toLowerCase())) ||
                                (group.getKeyLemmas() != null && group.getKeyLemmas().stream()
                                        .anyMatch(lemma -> lemma.toLowerCase().contains(query.toLowerCase())))
                )
                .sorted((g1, g2) -> Integer.compare(g2.getFrequency(), g1.getFrequency()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Получить все группы тем
     */
    public List<TopicGroup> getAllTopicGroups() {
        return topicGroupRepository.findAll();
    }

}