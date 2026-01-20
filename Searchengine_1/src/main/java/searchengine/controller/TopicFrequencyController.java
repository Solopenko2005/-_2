// ==================== НОВЫЙ ФОРМАТ С ССЫЛКАМИ ====================

package searchengine.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.TopicFrequencyDto;
import searchengine.dto.TopicLinkDto;
import searchengine.model.Topic;
import searchengine.model.TopicGroup;
import searchengine.repository.TopicRepository;
import searchengine.services.TopicFilterService;
import searchengine.services.TopicGroupingService;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/topics")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TopicFrequencyController {

    private final TopicGroupingService topicGroupingService;
    private final TopicFilterService topicFilterService;
    private final TopicRepository topicRepository; // Добавьте эту зависимость

    // ... ВСЕ ВАШИ СУЩЕСТВУЮЩИЕ МЕТОДЫ ...

    // ==================== НОВЫЙ ФОРМАТ ВЫВОДА ====================

    /**
     * Получить темы в новом формате с ссылками
     * GET /api/topics/with-links?limit=20
     * Формат: Группа, Тема, Частота, Количество сайтов, Ссылки
     */
    /**
     * Получить детальную информацию по группе с ссылками
     * GET /api/topics/{groupId}/links
     */
    @GetMapping("/{groupId}/links")
    public ResponseEntity<Map<String, Object>> getGroupWithLinks(@PathVariable int groupId) {
        Map<String, Object> response = new HashMap<>();

        try {
            TopicGroup group = topicGroupingService.getTopicGroupById(groupId);

            if (group == null) {
                response.put("result", false);
                response.put("error", "Группа с id=" + groupId + " не найдена");
                return ResponseEntity.status(404).body(response);
            }

            TopicFrequencyDto dto = convertToTopicFrequencyDto(group);

            response.put("result", true);
            response.put("data", dto);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("result", false);
            response.put("error", "Не удалось получить данные группы: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Поиск тем в новом формате с ссылками
     * GET /api/topics/search-with-links?query=агро
     */
    @GetMapping("/search-with-links")
    public ResponseEntity<Map<String, Object>> searchTopicsWithLinks(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit) {

        Map<String, Object> response = new HashMap<>();

        try {
            List<TopicGroup> groups = topicGroupingService.searchTopicGroups(query, limit * 2);

            List<TopicFrequencyDto> result = groups.stream()
                    .filter(topicFilterService::isRelevantTopic)
                    .limit(limit)
                    .map(this::convertToTopicFrequencyDto)
                    .collect(Collectors.toList());

            response.put("result", true);
            response.put("data", result);
            response.put("count", result.size());
            response.put("query", query);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("result", false);
            response.put("error", "Не удалось выполнить поиск: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Конвертация группы тем в DTO с ссылками
     */
    private TopicFrequencyDto convertToTopicFrequencyDto(TopicGroup group) {
        TopicFrequencyDto dto = new TopicFrequencyDto();
        dto.setGroupId(group.getId());
        dto.setGroupTitle(group.getTitle());

        // В качестве названия темы используем заголовок группы
        // Или можно взять самый частый заголовок из тем группы
        dto.setTopicTitle(getMostCommonTopicTitle(group));

        dto.setFrequency(group.getFrequency());
        dto.setSiteCount(group.getSiteCount());

        // Получаем все темы этой группы
        List<Topic> topics = topicRepository.findByTopicGroupId(group.getId());

        // Собираем ссылки
        List<TopicLinkDto> links = topics.stream()
                .map(topic -> convertToLinkDto(topic))
                .collect(Collectors.toList());

        dto.setLinks(links);

        return dto;
    }

    /**
     * Получить самый частый заголовок темы в группе
     */
    private String getMostCommonTopicTitle(TopicGroup group) {
        List<Topic> topics = topicRepository.findByTopicGroupId(group.getId());

        if (topics.isEmpty()) {
            return group.getTitle();
        }

        // Группируем по заголовку и находим самый частый
        Map<String, Long> titleCounts = topics.stream()
                .collect(Collectors.groupingBy(
                        Topic::getTitle,
                        Collectors.counting()
                ));

        return titleCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(group.getTitle());
    }

    /**
     * Конвертация темы в DTO ссылки
     */
    private TopicLinkDto convertToLinkDto(Topic topic) {
        TopicLinkDto link = new TopicLinkDto();

        // Основная информация
        link.setTopicTitle(topic.getTitle());
        link.setSiteName(topic.getSite().getName());
        link.setPagePath(topic.getPage().getPath());

        // Формируем полный URL
        String siteUrl = topic.getSite().getUrl();
        String pagePath = topic.getPage().getPath();

        // Убедимся, что URL корректен
        if (siteUrl.endsWith("/") && pagePath.startsWith("/")) {
            siteUrl = siteUrl.substring(0, siteUrl.length() - 1);
        } else if (!siteUrl.endsWith("/") && !pagePath.startsWith("/") && !pagePath.isEmpty()) {
            siteUrl = siteUrl + "/";
        }

        String fullUrl = siteUrl + pagePath;
        link.setUrl(fullUrl);

        // Дополнительная информация
        link.setCreatedAt(topic.getCreatedAt());
        link.setLemmaCount(topic.getLemmaCount());

        return link;
    }
    @GetMapping("/with-links")
    public ResponseEntity<Map<String, Object>> getTopicsWithLinks(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "2") int minFrequency,
            @RequestParam(defaultValue = "1") int minSiteCount,
            @RequestParam(defaultValue = "true") boolean excludeSystem) {

        Map<String, Object> response = new HashMap<>();

        try {
            // Получаем группы тем
            List<TopicGroup> allGroups = topicGroupingService.getAllTopicGroups();
            System.out.println("Всего групп: " + allGroups.size());

            // Фильтруем и сортируем
            List<TopicGroup> filteredGroups = allGroups.stream()
                    .filter(group -> group.getFrequency() >= minFrequency)
                    .filter(group -> group.getSiteCount() >= minSiteCount)
                    .filter(group -> !excludeSystem || topicFilterService.isRelevantTopic(group))
                    .sorted((g1, g2) -> Integer.compare(g2.getFrequency(), g1.getFrequency()))
                    .limit(limit)
                    .collect(Collectors.toList());

            System.out.println("Отфильтровано групп: " + filteredGroups.size());

            // Конвертируем в нужный формат
            List<TopicFrequencyDto> result = new ArrayList<>();
            int totalLinks = 0;

            for (TopicGroup group : filteredGroups) {
                System.out.println("Обработка группы ID=" + group.getId() +
                        ", Title=" + group.getTitle() +
                        ", Frequency=" + group.getFrequency());

                TopicFrequencyDto dto = convertToTopicFrequencyDto(group);
                result.add(dto);

                // Считаем общее количество ссылок
                if (dto.getLinks() != null) {
                    totalLinks += dto.getLinks().size();
                    System.out.println("  Ссылок найдено: " + dto.getLinks().size());
                } else {
                    System.out.println("  Ссылок нет (null)");
                }
            }

            // Статистика
            long totalRelevant = allGroups.stream()
                    .filter(group -> topicFilterService.isRelevantTopic(group))
                    .count();

            // Формируем полный ответ
            response.put("result", true);
            response.put("data", result);
            response.put("count", result.size());
            response.put("totalLinks", totalLinks);
            response.put("totalRelevant", totalRelevant);
            response.put("totalAll", allGroups.size());
            response.put("filters", Map.of(
                    "limit", limit,
                    "minFrequency", minFrequency,
                    "minSiteCount", minSiteCount,
                    "excludeSystem", excludeSystem
            ));
            response.put("format", "group-theme-frequency-sites-links");
            response.put("timestamp", new Date());

            // Добавляем отладочную информацию
            response.put("debug", Map.of(
                    "groupsTotal", allGroups.size(),
                    "groupsFiltered", filteredGroups.size(),
                    "averageFrequency", filteredGroups.isEmpty() ? 0 :
                            filteredGroups.stream().mapToInt(TopicGroup::getFrequency).average().orElse(0),
                    "averageSiteCount", filteredGroups.isEmpty() ? 0 :
                            filteredGroups.stream().mapToInt(TopicGroup::getSiteCount).average().orElse(0)
            ));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("Ошибка в /with-links: " + e.getMessage());
            e.printStackTrace();

            response.put("result", false);
            response.put("error", "Не удалось получить темы с ссылками: " + e.getMessage());
            response.put("exception", e.getClass().getName());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Получить темы по категории с ссылками
     * GET /api/topics/category-with-links?category=животноводство
     */
    @GetMapping("/category-with-links")
    public ResponseEntity<Map<String, Object>> getCategoryTopicsWithLinks(
            @RequestParam String category,
            @RequestParam(defaultValue = "10") int limit) {

        Map<String, Object> response = new HashMap<>();

        try {
            List<TopicGroup> allGroups = topicGroupingService.getAllTopicGroups();

            Map<String, String> categoryPatterns = getCategoryPatterns();
            String pattern = categoryPatterns.getOrDefault(category.toLowerCase(), category);

            List<TopicFrequencyDto> result = allGroups.stream()
                    .filter(group -> group.getTitle().toLowerCase().matches(".*(" + pattern + ").*") ||
                            (group.getContentSummary() != null &&
                                    group.getContentSummary().toLowerCase().matches(".*(" + pattern + ").*")))
                    .filter(topicFilterService::isRelevantTopic)
                    .sorted((g1, g2) -> Integer.compare(g2.getFrequency(), g1.getFrequency()))
                    .limit(limit)
                    .map(this::convertToTopicFrequencyDto)
                    .collect(Collectors.toList());

            response.put("result", true);
            response.put("category", category);
            response.put("data", result);
            response.put("count", result.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("result", false);
            response.put("error", "Не удалось получить темы по категории: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    // В классе TopicFrequencyController измените этот метод:

    // Измените с private на public
    public Map<String, String> getCategoryPatterns() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("животноводство", "животноводство|птицеводство|свиноводство|крс|овцеводство|скотоводство|животные|корма|ветеринария");
        patterns.put("растениеводство", "растениеводство|агрономия|урожай|посев|зерно|пшеница|кукуруза|соя|картофель|овощи");
        patterns.put("техника", "техника|комбайн|трактор|оборудование|машина|ии|искусственный интеллект|робот|автоматизация|цифровизация");
        patterns.put("экспорт", "экспорт|импорт|внешняя торговля|поставки|таможня|санкции|рынок");
        patterns.put("наука", "ученые|исследование|разработка|наука|открытие|изобретение|генетика|селекция|биотехнологии");
        patterns.put("экономика", "экономика|инвестиции|финансы|бюджет|кредит|субсидия|поддержка|дотация");
        patterns.put("конференции", "конференция|форум|мероприятие|совещание|встреча|саммит|выставка");
        patterns.put("экология", "экология|устойчивое развитие|био|органический|эко|природный|климат");
        patterns.put("политика", "политика|закон|регулирование|министерство|правительство|стратегия|программа");
        patterns.put("образование", "образование|университет|институт|студенты|академия|курсы|обучение");
        return patterns;
    }
    /**
     * Проверить связь тем с группами
     * GET /api/topics/check-links
     */
    @GetMapping("/check-links")
    public ResponseEntity<Map<String, Object>> checkTopicLinks(@RequestParam(defaultValue = "5") int limit) {
        Map<String, Object> response = new HashMap<>();

        try {
            // 1. Проверим группы
            List<TopicGroup> groups = topicGroupingService.getAllTopicGroups();

            // 2. Проверим, есть ли темы с группами
            long totalTopics = topicRepository.count();
            long topicsWithGroup = topicRepository.findAll().stream()
                    .filter(topic -> topic.getTopicGroup() != null)
                    .count();

            // 3. Проверим первые несколько групп
            List<Map<String, Object>> groupAnalysis = new ArrayList<>();

            for (int i = 0; i < Math.min(limit, groups.size()); i++) {
                TopicGroup group = groups.get(i);

                // Находим темы по заголовку (вместо по группе)
                List<Topic> topics = topicRepository.findAll().stream()
                        .filter(topic -> topic.getTitle().contains(group.getTitle()) ||
                                group.getTitle().contains(topic.getTitle()))
                        .limit(10)
                        .collect(Collectors.toList());

                Map<String, Object> analysis = new HashMap<>();
                analysis.put("groupId", group.getId());
                analysis.put("groupTitle", group.getTitle());
                analysis.put("groupFrequency", group.getFrequency());
                analysis.put("topicsByTitle", topics.size());
                analysis.put("topicsByGroupId", topicRepository.findByTopicGroupId(group.getId()).size());
                analysis.put("titles", topics.stream()
                        .map(Topic::getTitle)
                        .collect(Collectors.toList()));

                groupAnalysis.add(analysis);
            }

            response.put("result", true);
            response.put("statistics", Map.of(
                    "totalTopics", totalTopics,
                    "topicsWithGroup", topicsWithGroup,
                    "topicsWithoutGroup", totalTopics - topicsWithGroup,
                    "totalGroups", groups.size()
            ));
            response.put("groupAnalysis", groupAnalysis);
            response.put("diagnosis", topicsWithGroup == 0 ?
                    "⚠️ ПРОБЛЕМА: Нет тем, связанных с группами!" :
                    "✅ Темы связаны с группами");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("result", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    @GetMapping("/frequency-stats")
    public ResponseEntity<Map<String, Object>> getFrequencyStats(
            @RequestParam(defaultValue = "20") int limit) {

        Map<String, Object> response = new HashMap<>();

        try {
            List<TopicGroup> groups = topicGroupingService.getAllTopicGroups();

            List<Map<String, Object>> stats = groups.stream()
                    .filter(topicFilterService::isRelevantTopic)
                    .sorted((g1, g2) -> Integer.compare(g2.getFrequency(), g1.getFrequency()))
                    .limit(limit)
                    .map(group -> {
                        // Получаем все темы для подсчета оригинальных названий
                        List<Topic> topics = topicRepository.findByTopicGroupId(group.getId());

                        return Map.of(
                                "theme", group.getTitle(),
                                "frequency", group.getFrequency(),
                                "siteCount", group.getSiteCount(),
                                "originalTitles", topics.stream()
                                        .map(Topic::getTitle)
                                        .distinct()
                                        .collect(Collectors.toList()),
                                "totalReferences", topics.size(),
                                "groupId", group.getId()
                        );
                    })
                    .collect(Collectors.toList());

            response.put("result", true);
            response.put("data", stats);
            response.put("count", stats.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("result", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

}