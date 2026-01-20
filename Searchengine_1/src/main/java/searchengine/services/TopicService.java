package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import searchengine.dto.TopicStatisticsDto;
import searchengine.model.Topic;
import searchengine.model.TopicGroup;
import searchengine.repository.SiteRepository;
import searchengine.repository.TopicGroupRepository;
import searchengine.repository.TopicRepository;

import javax.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TopicService {

    private final TopicRepository topicRepository;
    private final SiteRepository siteRepository;
    private final TopicGroupingService topicGroupingService; // ДОБАВЛЕНО
    private final TopicGroupRepository topicGroupRepository; // ДОБАВЛЕНО

    public List<Topic> getPopularTopics(String siteUrl, int limit, int offset) {
        Pageable pageable = PageRequest.of(offset / limit, limit);

        if (siteUrl != null && !siteUrl.isEmpty()) {
            return siteRepository.findByUrl(siteUrl)
                    .map(site -> topicRepository.findPopularTopicsBySite(site.getId(), pageable))
                    .orElse(List.of());
        } else {
            return topicRepository.findPopularTopics(pageable);
        }
    }

    public TopicStatisticsDto getTopicStatistics(String siteUrl) {
        TopicStatisticsDto statistics = new TopicStatisticsDto();

        if (siteUrl != null && !siteUrl.isEmpty()) {
            siteRepository.findByUrl(siteUrl).ifPresent(site -> {
                Object[] stats = topicRepository.getTopicStatistics(site.getId());
                if (stats != null && stats.length >= 2) {
                    statistics.setTotalTopics(((Number) stats[0]).intValue());
                    statistics.setTotalLemmaCount(((Number) stats[1]).longValue());

                    List<Topic> topTopics = topicRepository.findPopularTopicsBySite(
                            site.getId(), PageRequest.of(0, 1));
                    if (!topTopics.isEmpty()) {
                        statistics.setMostFrequentTopic(topTopics.get(0).getTitle());
                    }

                    if (statistics.getTotalTopics() > 0) {
                        statistics.setAverageLemmaPerTopic(
                                (double) statistics.getTotalLemmaCount() / statistics.getTotalTopics());
                    }
                }
            });
        } else {
            long totalTopics = topicRepository.count();
            statistics.setTotalTopics((int) totalTopics);

            // Примерный подсчет для всех сайтов
            long totalLemmas = topicRepository.findAll().stream()
                    .mapToLong(Topic::getLemmaCount)
                    .sum();
            statistics.setTotalLemmaCount(totalLemmas);

            List<Topic> topTopics = topicRepository.findPopularTopics(PageRequest.of(0, 1));
            if (!topTopics.isEmpty()) {
                statistics.setMostFrequentTopic(topTopics.get(0).getTitle());
            }

            if (statistics.getTotalTopics() > 0) {
                statistics.setAverageLemmaPerTopic(
                        (double) statistics.getTotalLemmaCount() / statistics.getTotalTopics());
            }
        }

        return statistics;
    }

    public List<Topic> searchTopics(String query, String siteUrl, int limit) {
        Pageable pageable = PageRequest.of(0, limit);

        if (siteUrl != null && !siteUrl.isEmpty()) {
            return siteRepository.findByUrl(siteUrl)
                    .map(site -> topicRepository.searchByTitle(site.getId(), query, pageable))
                    .orElse(List.of());
        } else {
            return topicRepository.findAll().stream()
                    .filter(topic -> topic.getTitle().toLowerCase().contains(query.toLowerCase()))
                    .limit(limit)
                    .toList();
        }
    }
    /**
     * Получить частоту использования тем
     */
    public List<TopicGroup> getTopicFrequency(int limit, int minFrequency) {
        return topicGroupingService.getPopularTopicGroups(limit).stream()
                .filter(group -> group.getFrequency() >= minFrequency)
                .collect(Collectors.toList());
    }

    /**
     * Обновить группировку всех тем
     */
    @Transactional
    public void updateTopicGrouping() {
        topicGroupingService.groupAllTopics();
    }
}