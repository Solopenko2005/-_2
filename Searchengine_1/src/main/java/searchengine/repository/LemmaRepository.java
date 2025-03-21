package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;
import searchengine.model.Site;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Long> {

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO lemma (lemma, site_id, frequency) VALUES (:lemma, :siteId, 1) " +
            "ON CONFLICT (lemma, site_id) DO UPDATE " +
            "SET frequency = EXCLUDED.frequency + 1",
            nativeQuery = true)
    int upsertLemma(@Param("lemma") String lemma, @Param("siteId") Integer siteId);

    Optional<Lemma> findByLemmaAndSite(String lemma, Site site);

    @Query("SELECT l FROM Lemma l WHERE l.lemma = :lemma")
    List<Lemma> findAllByLemma(@Param("lemma") String lemma);

    @Query("SELECT l FROM Lemma l WHERE l.lemma = :lemma AND l.site = :site")
    List<Lemma> findAllByLemmaAndSite(@Param("lemma") String lemma, @Param("site") Site site);

    int countBySite(Site site);

    @Modifying
    @Query("DELETE FROM Lemma l WHERE l.site = :site")
    void deleteBySite(@Param("site") Site site);

    @Query("SELECT l FROM Lemma l WHERE l.lemma = :lemma AND l.site.id = :siteId")
    Optional<Lemma> findUniqueLemma(@Param("lemma") String lemma, @Param("siteId") int siteId);

    @Query("SELECT SUM(l.frequency) FROM Lemma l WHERE l.lemma = :lemma")
    Optional<Integer> getTotalFrequency(@Param("lemma") String lemma);

}