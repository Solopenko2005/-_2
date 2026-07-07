package searchengine.repository;

import searchengine.model.Topic;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.TopicGroup;

import java.util.List;

@Repository
public interface TopicRepository extends JpaRepository<Topic, Integer> {

    List<Topic> findByPageId(int pageId);

    List<Topic> findBySiteId(int siteId);

    void deleteByPageId(int pageId);

    void deleteBySiteId(int siteId);

    @Query("SELECT t FROM Topic t WHERE t.site.id = :siteId ORDER BY t.lemmaCount DESC")
    List<Topic> findPopularTopicsBySite(@Param("siteId") int siteId, Pageable pageable);

    @Query("SELECT t FROM Topic t ORDER BY t.lemmaCount DESC")
    List<Topic> findPopularTopics(Pageable pageable);

    @Query(value = "SELECT COUNT(t) as topicCount, COALESCE(SUM(t.lemmaCount), 0) as totalLemmas FROM Topic t WHERE t.site.id = :siteId")
    Object[] getTopicStatistics(@Param("siteId") int siteId);

    @Query("SELECT t FROM Topic t WHERE t.site.id = :siteId AND LOWER(t.title) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Topic> searchByTitle(@Param("siteId") int siteId, @Param("query") String query, Pageable pageable);

    long countByPageId(int pageId);

    long countBySiteId(int id);
    // Добавьте этот метод:
    List<Topic> findByTopicGroup(TopicGroup topicGroup);

    // Метод для поиска тем без группы
    @Query("SELECT t FROM Topic t WHERE t.topicGroup IS NULL")
    List<Topic> findTopicsWithoutGroup();

    // Метод для группировки по заголовку (простая статистика)
    @Query("SELECT t.title, COUNT(t) as frequency, COUNT(DISTINCT t.site.id) as siteCount " +
            "FROM Topic t " +
            "GROUP BY t.title " +
            "HAVING COUNT(t) > 1 " +
            "ORDER BY frequency DESC")
    List<Object[]> findTopicsByTitleFrequency();
    @Query("SELECT t FROM Topic t WHERE t.topicGroup.id = :groupId")
    List<Topic> findByTopicGroupId(@Param("groupId") int groupId);

    // Альтернативный метод с JOIN
    @Query("SELECT t FROM Topic t JOIN t.topicGroup g WHERE g.id = :groupId")
    List<Topic> findTopicsByGroupId(@Param("groupId") int groupId);
}