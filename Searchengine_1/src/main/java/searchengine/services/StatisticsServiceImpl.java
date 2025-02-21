package searchengine.services;

import org.springframework.stereotype.Service;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repository.SiteRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.LemmaRepository;

import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StatisticsServiceImpl implements StatisticsService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;

    public StatisticsServiceImpl(SiteRepository siteRepository, PageRepository pageRepository, LemmaRepository lemmaRepository) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
    }

    @Override
    public StatisticsResponse getStatistics() {
        // Создаем объект общей статистики
        TotalStatistics totalStatistics = new TotalStatistics();
        totalStatistics.setSites((int) siteRepository.count());
        totalStatistics.setPages((int) pageRepository.count());
        totalStatistics.setLemmas((int) lemmaRepository.count());
        totalStatistics.setIndexing(!siteRepository.findByStatus(Status.INDEXING).isEmpty());

        // Формируем детальную статистику по сайтам
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

        // Объединяем общую и детальную статистику в один объект
        StatisticsData statisticsData = new StatisticsData();
        statisticsData.setTotal(totalStatistics);
        statisticsData.setDetailed(detailed);

        // Формируем итоговый ответ
        StatisticsResponse response = new StatisticsResponse();
        response.setResult(true);
        response.setStatistics(statisticsData);

        return response;
    }
}
