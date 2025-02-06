package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.Page;
import searchengine.model.SearchIndex;

import java.util.List;
import java.util.Set;

public interface IndexRepository extends JpaRepository<SearchIndex, Long> {

    @Query("SELECT si.page FROM SearchIndex si WHERE si.lemma.lemma IN :lemmas")
    List<Page> findPagesByLemmas(@Param("lemmas") List<String> lemmas);

    @Query("SELECT SUM(si.ranking) FROM SearchIndex si WHERE si.page = :page AND si.lemma.lemma IN :lemmas")
    Float getRelevanceForPage(@Param("page") Page page, @Param("lemmas") List<String> lemmas);

    @Query("SELECT si FROM SearchIndex si WHERE si.page = :page AND si.lemma.lemma IN :lemmas")
    List<SearchIndex> findIndexesForPageAndLemmas(@Param("page") Page page, @Param("lemmas") Set<String> lemmas);

    List<Page> findPagesByLemma(String s);

}
