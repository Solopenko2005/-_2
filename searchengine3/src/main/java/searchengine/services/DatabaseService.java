package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;

@Service
public class DatabaseService {

    private final PageRepository pageRepository;

    @Autowired
    public DatabaseService(SiteRepository siteRepository, PageRepository pageRepository) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
    }
    @Autowired
    private SiteRepository siteRepository;

    public void updateSiteStatus(Site site, String errorMessage) {
        siteRepository.updateSiteStatus(site.getId(), Status.FAILED, LocalDateTime.now(), errorMessage);
    }


    // Сохранение нового сайта
    public void saveSite(Site site) {
        siteRepository.save(site);
    }

    // Обновление данных сайта
    public void updateSite(Site site) {
        siteRepository.save(site);
    }

    // Сохранение новой страницы
    public void savePage(Page page) {
        pageRepository.save(page);
    }

    // Очистка всех данных из базы
    public void clearAllData() {
        pageRepository.deleteAll();
        siteRepository.deleteAll();
    }

    public void saveLemma(String lemma, Site site) {
    }
}
