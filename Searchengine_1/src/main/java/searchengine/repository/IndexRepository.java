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
import java.util.Set;

public interface IndexRepository extends JpaRepository<SearchIndex, Long> {

    @Query("SELECT si.page FROM SearchIndex si WHERE si.lemma.lemma IN :lemmas")
    List<Page> findPagesByLemmas(@Param("lemmas") List<String> lemmas);

    @Query("SELECT SUM(si.ranking) FROM SearchIndex si WHERE si.page = :page AND si.lemma.lemma IN :lemmas")
    Float getRelevanceForPage(@Param("page") Page page, @Param("lemmas") List<String> lemmas);

    @Query("SELECT si FROM SearchIndex si WHERE si.page = :page AND si.lemma.lemma IN :lemmas")
    List<SearchIndex> findIndexesForPageAndLemmas(@Param("page") Page page, @Param("lemmas") Set<String> lemmas);

    List<Page> findPagesByLemma(Lemma s);

    @Query("SELECT si.ranking FROM SearchIndex si WHERE si.page = :page AND si.lemma = :lemma")
    Float findRankByPageAndLemma(@Param("page") Page page, @Param("lemma") Lemma lemma);

    List<SearchIndex> findByPage(Page page);
    @Modifying
    @Query(value = "DELETE FROM search_index si USING page p WHERE si.page_id = p.id AND p.site_id = :siteId", nativeQuery = true)
    void deleteByPage_Site(@Param("siteId") Integer siteId);

    @Query("SELECT DISTINCT si.page FROM SearchIndex si WHERE si.lemma.lemma IN :lemmas AND si.page.site = :site")
    List<Page> findPagesByLemmasAndSite(@Param("lemmas") List<String> lemmas, @Param("site") Site site);
}
