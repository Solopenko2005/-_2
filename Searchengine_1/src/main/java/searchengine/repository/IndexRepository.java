package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.List;

public interface IndexRepository extends JpaRepository<Index, Integer> {

    // Поиск всех индексов для конкретной страницы
    List<Index> findByPage(Page page);

    // Проверка существования индекса для страницы и леммы
    @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM Index i WHERE i.page = :page AND i.lemma = :lemma")
    boolean existsByPageAndLemma(@Param("page") Page page, @Param("lemma") Lemma lemma);

    // Поиск индекса по странице и лемме
    @Query("SELECT i FROM Index i WHERE i.page = :page AND i.lemma = :lemma")
    Index findByPageAndLemma(@Param("page") Page page, @Param("lemma") Lemma lemma);

    // Поиск страниц по лемме и сайту
    @Query("SELECT i.page FROM Index i WHERE i.page.site = :site AND i.lemma = :lemma")
    List<Page> findPagesByLemmaAndSite(@Param("site") Site site, @Param("lemma") Lemma lemma);

    // Поиск индексов по лемме
    @Query("SELECT i FROM Index i WHERE i.lemma = :lemma")
    List<Index> findByLemma(@Param("lemma") Lemma lemma);

    // Поиск индексов по странице и лемме (с возвращением ранга)
    @Query("SELECT i.rank FROM Index i WHERE i.page = :page AND i.lemma = :lemma")
    Float findRankByPageAndLemma(@Param("page") Page page, @Param("lemma") Lemma lemma);

    // Поиск страниц по лемме
    @Query("SELECT i.page FROM Index i WHERE i.lemma = :lemma")
    List<Page> findPagesByLemma(@Param("lemma") Lemma lemma);
}