package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.Optional;

@Repository
public interface PageRepository extends JpaRepository<Page, Long> {
    int countBySite(Site site);

    boolean existsBySiteAndPath(Site site, String path);

    @Query("SELECT p FROM Page p WHERE p.site.id = :siteId AND p.path = :path")
    Optional<Page> findBySiteAndPath(@Param("siteId") int siteId, @Param("path") String path);
}