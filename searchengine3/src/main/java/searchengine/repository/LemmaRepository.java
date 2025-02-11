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
import java.util.Set;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Long> {

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO lemma (lemma, site_id, frequency) VALUES (:lemma, :siteId, 1) " +
            "ON CONFLICT (lemma, site_id) DO UPDATE " +
            "SET frequency = EXCLUDED.frequency + 1",
            nativeQuery = true)
    int upsertLemma(@Param("lemma") String lemma, @Param("siteId") Integer siteId);

    Optional<Lemma> findByLemma(String lemma);

    Optional<Lemma> findByLemmaAndSite(String lemma, Site site);
    long countBySite(Site site);

}

