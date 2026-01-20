package searchengine.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import searchengine.services.AdvancedTopicGroupingService;

import static searchengine.services.LemmaService.logger;

@Configuration
@EnableScheduling
public class TopicGroupingConfig {

    private final AdvancedTopicGroupingService groupingService;

    public TopicGroupingConfig(AdvancedTopicGroupingService groupingService) {
        this.groupingService = groupingService;
    }

    /**
     * Автоматическая перегруппировка раз в сутки
     */
    @Scheduled(cron = "0 0 2 * * ?") // Каждый день в 2:00 ночи
    public void scheduleDailyRegrouping() {
        try {
            groupingService.regroupTopics();
        } catch (Exception e) {
            logger.error("Ошибка автоматической перегруппировки", e);
        }
    }
}