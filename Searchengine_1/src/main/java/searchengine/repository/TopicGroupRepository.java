package searchengine.repository;

import searchengine.model.TopicGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TopicGroupRepository extends JpaRepository<TopicGroup, Integer> {

    @Query("SELECT tg FROM TopicGroup tg JOIN tg.keyLemmas kl WHERE kl IN :lemmas GROUP BY tg HAVING COUNT(DISTINCT kl) >= :minMatch")
    List<TopicGroup> findByMatchingLemmas(@Param("lemmas") List<String> lemmas, @Param("minMatch") int minMatch);

    @Query("SELECT tg FROM TopicGroup tg ORDER BY tg.frequency DESC, tg.siteCount DESC")
    List<TopicGroup> findPopularTopicGroups();

    Optional<TopicGroup> findByTitle(String title);
}