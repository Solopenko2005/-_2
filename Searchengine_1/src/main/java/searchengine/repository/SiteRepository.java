package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Site;
import searchengine.model.Status;

import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SiteRepository extends JpaRepository<Site, Integer> {

    List<Site> findByStatus(Status status);
    @Modifying
    @Transactional
    @Query("UPDATE Site s SET s.status = :status, s.statusTime = :statusTime, s.lastError = :lastError WHERE s.id = :id")
    void updateSiteStatus(@Param("id") int id, @Param("status") Status status, @Param("statusTime") LocalDateTime statusTime, @Param("lastError") String lastError);
    Site findSiteByUrl(String url);

    Optional<Site> findByUrl(String siteUrl);
}
