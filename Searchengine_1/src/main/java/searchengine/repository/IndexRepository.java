package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.SearchIndex;
import searchengine.model.Site;

import java.util.List;

public interface IndexRepository extends JpaRepository<SearchIndex, Long> {
    @Query("SELECT si.page FROM SearchIndex si WHERE si.lemma.lemma IN :lemmas")
    List<Page> findPagesByLemmas(@Param("lemmas") List<String> lemmas);

    @Query("SELECT si.ranking FROM SearchIndex si WHERE si.page = :page AND si.lemma = :lemma")
    Float findRankByPageAndLemma(@Param("page") Page page, @Param("lemma") Lemma lemma);

    List<SearchIndex> findByPage(Page page);

    @Query("SELECT DISTINCT si.page FROM SearchIndex si WHERE si.lemma.lemma IN :lemmas AND si.page.site = :site")
    List<Page> findPagesByLemmasAndSite(@Param("lemmas") List<String> lemmas, @Param("site") Site site);
}