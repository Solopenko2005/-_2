package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Status;
import searchengine.repository.SiteRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.LemmaRepository;

import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;
@RequiredArgsConstructor
@Service
public class StatisticsServiceImpl implements StatisticsService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;

    @Override
    public StatisticsResponse getStatistics() {
        TotalStatistics totalStatistics = new TotalStatistics();
        totalStatistics.setSites((int) siteRepository.count());
        totalStatistics.setPages((int) pageRepository.count());
        totalStatistics.setLemmas((int) lemmaRepository.count());
        totalStatistics.setIndexing(!siteRepository.findByStatus(Status.INDEXED).isEmpty());

        List<DetailedStatisticsItem> detailed = siteRepository.findAll().stream()
                .map(site -> {
                    DetailedStatisticsItem item = new DetailedStatisticsItem();
                    item.setUrl(site.getUrl());
                    item.setName(site.getName());
                    item.setStatus(site.getStatus().toString());
                    item.setStatusTime(site.getStatusTime() != null
                            ? site.getStatusTime().toEpochSecond(ZoneOffset.UTC) * 1000
                            : 0);
                    item.setError(site.getLastError());
                    item.setPages(pageRepository.countBySite(site));
                    item.setLemmas(lemmaRepository.countBySite(site));
                    return item;
                })
                .collect(Collectors.toList());

        StatisticsData statisticsData = new StatisticsData();
        statisticsData.setTotal(totalStatistics);
        statisticsData.setDetailed(detailed);

        StatisticsResponse response = new StatisticsResponse();
        response.setResult(true);
        response.setStatistics(statisticsData);

        return response;
    }
}
