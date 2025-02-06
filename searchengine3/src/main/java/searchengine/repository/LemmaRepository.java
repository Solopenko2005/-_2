package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.Lemma;
import searchengine.model.Site;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface LemmaRepository extends JpaRepository<Lemma, Long> {

    Optional<Lemma> findByLemmaAndSite(String lemma, Site site);

    // Запрос для получения частот лемм
    @Query("SELECT l.lemma, SUM(l.frequency) FROM Lemma l WHERE l.lemma IN :lemmas GROUP BY l.lemma")
    List<Object[]> getLemmaFrequencies(@Param("lemmas") Set<String> lemmas);

    // Поиск леммы по имени
    Lemma findByLemma(String lemma);

    // Обновление частоты леммы (если лемма существует)
    @Query("UPDATE Lemma l SET l.frequency = l.frequency + 1 WHERE l.lemma = :lemma")
    void incrementFrequency(@Param("lemma") String lemma);

    // Добавление новой леммы, если она не существует (через метод save)
    Lemma save(Lemma lemma);

}
