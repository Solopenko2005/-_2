package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.Lemma;
import searchengine.model.Site;

import java.util.List;
import java.util.Optional;

public interface LemmaRepository extends JpaRepository<Lemma, Integer> {

    // Подсчёт количества лемм для конкретного сайта
    int countBySite(Site site);

    // Поиск лемм по значению и сайту
    @Query("SELECT l FROM Lemma l WHERE l.lemma = :lemma AND l.site = :site")
    List<Lemma> findByLemmaAndSite(@Param("lemma") String lemma, @Param("site") Site site);

    // Поиск лемм по значению (без привязки к сайту)
    @Query("SELECT l FROM Lemma l WHERE l.lemma = :lemma")
    List<Lemma> findByLemma(@Param("lemma") String lemma);

    // Поиск леммы по значению и сайту (возвращает Optional)
    @Query("SELECT l FROM Lemma l WHERE l.lemma = :lemma AND l.site = :site")
    Optional<Lemma> findOneByLemmaAndSite(@Param("lemma") String lemma, @Param("site") Site site);

    // Поиск лемм по сайту
    @Query("SELECT l FROM Lemma l WHERE l.site = :site")
    List<Lemma> findBySite(@Param("site") Site site);

}