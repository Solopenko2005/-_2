package searchengine.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.StatisticsService;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class StatisticsController {
    private final StatisticsService statisticsService;

    @GetMapping("/statistics")
    public StatisticsResponse getStatistics() {
        return statisticsService.getStatistics();
    }
}