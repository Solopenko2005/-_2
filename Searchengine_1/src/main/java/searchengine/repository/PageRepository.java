package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.Page;
import searchengine.model.Site;

public interface PageRepository extends JpaRepository<Page, Integer> {
    void deleteBySite(Site site);
    boolean existsBySiteAndPath(Site site, String path);
    int countBySite(Site site);
    Page findBySiteAndPath(Site site, String replace);
}