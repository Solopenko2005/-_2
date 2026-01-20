package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.Topic;

import java.util.List;

public interface EnhancedTopicRepository extends JpaRepository<Topic, Integer> {

    /**
     * Найти все темы группы с JOIN для оптимизации
     */
    @Query("SELECT t FROM Topic t " +
            "JOIN FETCH t.site " +
            "JOIN FETCH t.page " +
            "WHERE t.topicGroup.id = :groupId " +
            "ORDER BY t.site.name, t.createdAt DESC")
    List<Topic> findTopicsByGroupIdWithDetails(@Param("groupId") int groupId);

    /**
     * Найти темы по ключевым леммам
     */
    @Query("SELECT DISTINCT t FROM Topic t " +
            "JOIN t.topicGroup g " +
            "WHERE EXISTS (" +
            "  SELECT 1 FROM g.keyLemmas kl " +
            "  WHERE kl IN :lemmas" +
            ")")
    List<Topic> findTopicsByKeyLemmas(@Param("lemmas") List<String> lemmas);

    /**
     * Статистика по группам
     */
    @Query("SELECT g.id, g.title, COUNT(t) as topicCount, " +
            "COUNT(DISTINCT t.site.id) as siteCount " +
            "FROM TopicGroup g " +
            "LEFT JOIN g.topics t " +
            "GROUP BY g.id, g.title " +
            "HAVING COUNT(t) >= :minFrequency " +
            "ORDER BY siteCount DESC, topicCount DESC")
    List<Object[]> getGroupStatistics(@Param("minFrequency") int minFrequency);
}