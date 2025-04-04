package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.Lemma;
import searchengine.model.Site;

import java.util.List;
import java.util.Optional;

public interface LemmaRepository extends JpaRepository<Lemma, Long> {
    Optional<Lemma> findByLemmaAndSite(String lemma, Site site);
    Optional<Lemma> findByLemmaAndSiteId(String lemma, int siteId);

    int countBySite(Site site);

    List<Lemma> findAllByLemmaAndSite(String lemma, Site site);
    List<Lemma> findAllByLemma(String lemma);

    void deleteBySite(Site site); // Убираем @Query, используем стандартный метод

    @Modifying
    @Query(value = "INSERT INTO lemma (lemma, site_id, frequency) VALUES (:lemma, :siteId, 1) " +
            "ON CONFLICT (lemma, site_id) DO UPDATE " +
            "SET frequency = lemma.frequency + 1",
            nativeQuery = true)
    void upsertLemma(@Param("lemma") String lemma, @Param("siteId") Integer siteId);
}