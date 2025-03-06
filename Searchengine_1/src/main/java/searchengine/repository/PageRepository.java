package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.Optional;

@Repository
public interface PageRepository extends JpaRepository<Page, Long> {
    Optional<Page> findByPathAndSite(String path, Site site);
    int countBySite(Site site);
    boolean existsBySiteAndPath(Site site, String path);
    Page findBySiteAndPath(Site site, String replace);
    @Modifying
    @Query("DELETE FROM Page p WHERE p.site = :site")
    void deleteBySite(@Param("site") Site site);
}
