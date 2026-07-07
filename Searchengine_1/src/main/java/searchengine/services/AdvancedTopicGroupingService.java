package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Topic;
import searchengine.model.TopicGroup;
import searchengine.repository.TopicGroupRepository;
import searchengine.repository.TopicRepository;

import java.util.*;
import java.util.stream.Collectors;

import static searchengine.services.LemmaService.logger;

@Service
@RequiredArgsConstructor
public class AdvancedTopicGroupingService {

    private final TopicRepository topicRepository;
    private final TopicGroupRepository topicGroupRepository;
    private final TopicLemmatizerService lemmatizerService;
    private final TopicFilterService topicFilterService;

    private static final double SIMILARITY_THRESHOLD = 0.8; // 30% схожести
    private static final int MIN_GROUP_SIZE = 2;

    /**
     * Интеллектуальная группировка тем
     */
    @Transactional
    public void intelligentGroupTopics() {
        logger.info("Начало интеллектуальной группировки тем");

        // 1. Получаем все темы без группы
        List<Topic> ungroupedTopics = topicRepository.findTopicsWithoutGroup();
        logger.info("Найдено тем без группы: {}", ungroupedTopics.size());

        // 2. Подготавливаем данные: тема -> ключевые леммы
        Map<Topic, Set<String>> topicToLemmas = new HashMap<>();
        Map<String, List<Topic>> lemmaToTopics = new HashMap<>();

        for (Topic topic : ungroupedTopics) {
            Set<String> lemmas = lemmatizerService.extractKeyLemmas(topic.getTitle());
            topicToLemmas.put(topic, lemmas);

            // Инвертированный индекс: лемма -> список тем
            for (String lemma : lemmas) {
                lemmaToTopics.computeIfAbsent(lemma, k -> new ArrayList<>()).add(topic);
            }
        }

        // 3. Группируем похожие темы
        Set<Topic> processed = new HashSet<>();
        List<TopicGroup> newGroups = new ArrayList<>();

        for (Topic topic : ungroupedTopics) {
            if (processed.contains(topic)) continue;

            // Находим похожие темы через общие леммы
            Set<Topic> similarTopics = findSimilarTopics(topic, topicToLemmas, lemmaToTopics);

            if (similarTopics.size() >= MIN_GROUP_SIZE) {
                // Создаем группу
                TopicGroup group = createTopicGroup(similarTopics);
                newGroups.add(group);
                processed.addAll(similarTopics);

                logger.debug("Создана группа: {} с {} темами",
                        group.getTitle(), similarTopics.size());
            }
        }

        // 4. Сохраняем группы
        topicGroupRepository.saveAll(newGroups);

        logger.info("Создано новых групп: {}", newGroups.size());
        logger.info("Обработано тем: {}/{}", processed.size(), ungroupedTopics.size());
    }

    /**
     * Найти похожие темы для заданной темы
     */
    private Set<Topic> findSimilarTopics(Topic seedTopic,
                                         Map<Topic, Set<String>> topicToLemmas,
                                         Map<String, List<Topic>> lemmaToTopics) {

        Set<Topic> similarTopics = new HashSet<>();
        similarTopics.add(seedTopic);

        Set<String> seedLemmas = topicToLemmas.get(seedTopic);
        if (seedLemmas.isEmpty()) return similarTopics;

        // Ищем темы с общими леммами
        Set<Topic> candidateTopics = new HashSet<>();
        for (String lemma : seedLemmas) {
            List<Topic> topicsWithSameLemma = lemmaToTopics.getOrDefault(lemma, new ArrayList<>());
            candidateTopics.addAll(topicsWithSameLemma);
        }

        // Фильтруем по схожести
        for (Topic candidate : candidateTopics) {
            if (candidate.equals(seedTopic)) continue;

            Set<String> candidateLemmas = topicToLemmas.get(candidate);
            double similarity = lemmatizerService.calculateSimilarity(seedLemmas, candidateLemmas);

            if (similarity >= SIMILARITY_THRESHOLD) {
                similarTopics.add(candidate);
            }
        }

        return similarTopics;
    }

    /**
     * Создать группу из набора тем
     */
    private TopicGroup createTopicGroup(Set<Topic> topics) {
        TopicGroup group = new TopicGroup();

        // Определяем заголовок группы (самый частый заголовок)
        String groupTitle = determineGroupTitle(topics);
        group.setTitle(groupTitle);

        // Создаем краткое описание
        String summary = createGroupSummary(topics);
        group.setContentSummary(summary);

        // Устанавливаем частоту
        group.setFrequency(topics.size());

        // Считаем уникальные сайты
        long siteCount = topics.stream()
                .map(topic -> topic.getSite().getId())
                .distinct()
                .count();
        group.setSiteCount((int) siteCount);

        // Извлекаем ключевые леммы из всех тем
        Set<String> allKeyLemmas = extractAllKeyLemmas(topics);
        group.setKeyLemmas(allKeyLemmas);

        // Связываем темы с группой
        for (Topic topic : topics) {
            topic.setTopicGroup(group);
        }

        return group;
    }

    /**
     * Определить заголовок группы
     */
    private String determineGroupTitle(Set<Topic> topics) {
        // Группируем заголовки для нахождения самого частого
        Map<String, Long> titleFrequency = topics.stream()
                .collect(Collectors.groupingBy(Topic::getTitle, Collectors.counting()));

        // Возвращаем самый частый заголовок
        return titleFrequency.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseGet(() -> {
                    // Если все уникальные, берем самый короткий осмысленный
                    return topics.stream()
                            .map(Topic::getTitle)
                            .filter(t -> t.length() > 10 && t.length() < 100)
                            .findFirst()
                            .orElse("Разные темы");
                });
    }

    /**
     * Создать краткое описание группы
     */
    private String createGroupSummary(Set<Topic> topics) {
        if (topics.isEmpty()) return "";

        // Берем первые 3 темы для создания описания
        return topics.stream()
                .limit(3)
                .map(topic -> {
                    String content = topic.getContent();
                    return content.length() > 100 ? content.substring(0, 100) + "..." : content;
                })
                .collect(Collectors.joining(" | "));
    }

    /**
     * Извлечь все ключевые леммы из тем
     */
    private Set<String> extractAllKeyLemmas(Set<Topic> topics) {
        Set<String> allLemmas = new HashSet<>();

        for (Topic topic : topics) {
            Set<String> topicLemmas = lemmatizerService.extractKeyLemmas(
                    topic.getTitle() + " " + topic.getContent()
            );
            allLemmas.addAll(topicLemmas);
        }

        // Ограничиваем количество
        return allLemmas.stream()
                .limit(20)
                .collect(Collectors.toSet());
    }

    /**
     * Обновить существующие группы (перегруппировать)
     */
    @Transactional
    public void regroupTopics() {
        logger.info("Начало перегруппировки тем");

        // 1. Очищаем существующие связи
        List<Topic> allTopics = topicRepository.findAll();
        for (Topic topic : allTopics) {
            topic.setTopicGroup(null);
        }
        topicRepository.saveAll(allTopics);

        // 2. Удаляем старые группы
        topicGroupRepository.deleteAll();

        // 3. Выполняем новую группировку
        intelligentGroupTopics();

        logger.info("Перегруппировка завершена");
    }

    /**
     * Найти группы по запросу с улучшенным поиском
     */
    public List<TopicGroup> searchGroups(String query, int limit) {
        // Извлекаем леммы из запроса
        Set<String> queryLemmas = lemmatizerService.extractKeyLemmas(query);

        if (queryLemmas.isEmpty()) {
            return Collections.emptyList();
        }

        // Ищем группы, содержащие эти леммы
        List<TopicGroup> allGroups = topicGroupRepository.findAll();

        return allGroups.stream()
                .filter(group -> {
                    // Проверяем по заголовку
                    if (group.getTitle().toLowerCase().contains(query.toLowerCase())) {
                        return true;
                    }

                    // Проверяем по ключевым леммам
                    if (group.getKeyLemmas() != null) {
                        Set<String> groupLemmas = group.getKeyLemmas();
                        groupLemmas.retainAll(queryLemmas);
                        return !groupLemmas.isEmpty();
                    }

                    return false;
                })
                .sorted((g1, g2) -> Integer.compare(g2.getFrequency(), g1.getFrequency()))
                .limit(limit)
                .collect(Collectors.toList());
    }
    /**
     * Получить все группы, отсортированные
     */
    public List<TopicGroup> getAllGroupsSorted() {
        return topicGroupRepository.findAll().stream()
                .sorted((g1, g2) -> {
                    int siteCompare = Integer.compare(g2.getSiteCount(), g1.getSiteCount());
                    if (siteCompare != 0) return siteCompare;
                    return Integer.compare(g2.getFrequency(), g1.getFrequency());
                })
                .collect(Collectors.toList());
    }

    /**
     * Получить группу по ID
     */
    public TopicGroup getGroupById(int id) {
        return topicGroupRepository.findById(id).orElse(null);
    }
}