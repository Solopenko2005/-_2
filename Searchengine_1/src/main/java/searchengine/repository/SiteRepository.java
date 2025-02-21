package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.Site;
import searchengine.model.Status;

import java.util.List;
import java.util.Optional;


public interface SiteRepository extends JpaRepository<Site, Long> {
    List<Site> findAllByUrl(String url);

    Optional<Site> findByUrl(String url);
    List<Site> findByStatus(Status status);
}
