package searchengine.services;

import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.IndexingState;
import searchengine.model.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DatabaseService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);
    @PersistenceContext
    private EntityManager entityManager;
    private final IndexingState indexingState;
    @Autowired
    private JdbcTemplate jdbcTemplate;


    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    @Autowired
    public DatabaseService(IndexingState indexingState, SiteRepository siteRepository, PageRepository pageRepository, LemmaRepository lemmaRepository, IndexRepository indexRepository) {
        this.indexingState = indexingState;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
    }
    @Transactional
    public void truncateAllTables() {
        if (!indexingState.isStopRequested()) {
            jdbcTemplate.execute("TRUNCATE TABLE search_index, page, lemma, site CASCADE");
        }
    }

    // Сохранение сайта
    @Transactional(rollbackFor = Exception.class)
    public void saveSite(Site site) {
        try {
            siteRepository.save(site);
            logger.info("Сохранен сайт: {}", site.getUrl());
        } catch (DataAccessException e) {
            logger.error("Ошибка доступа к данным при сохранении сайта '{}': {}", site.getUrl(), e.getMessage(), e);
            throw e; // Пробрасываем исключение, чтобы транзакция откатилась
        }
    }

    @Transactional(rollbackFor = Exception.class, timeout = 5)
    public void savePage(Page page) {
        if (indexingState.isStopRequested()) {
            throw new RuntimeException("Индексация прервана");
        }
        entityManager.persist(page);
        entityManager.flush(); // Принудительная запись
        entityManager.clear(); // Сброс контекста
    }

    @Transactional
    public Lemma saveLemma(String lemmaText, Site site) {
        // Ищем лемму в базе данных
        Optional<Lemma> optionalLemma = lemmaRepository.findByLemmaAndSite(lemmaText, site);

        Lemma lemma;
        if (optionalLemma.isPresent()) {
            // Если лемма найдена, увеличиваем частоту
            lemma = optionalLemma.get();
            lemma.setFrequency(lemma.getFrequency() + 1);
        } else {
            // Если лемма не найдена, создаем новую запись
            lemma = new Lemma();
            lemma.setLemma(lemmaText);
            lemma.setFrequency(1);
            lemma.setSite(site);
        }

        // Сохраняем или обновляем лемму
        return lemmaRepository.save(lemma);
    }

    // Сохранение SearchIndex
    @Transactional
    public void saveSearchIndex(SearchIndex searchIndex) {
        try {
            indexRepository.save(searchIndex);
            logger.debug("Сохранен SearchIndex для страницы {} и леммы {}",
                    searchIndex.getPage().getId(), searchIndex.getLemma().getId());
        } catch (DataAccessException e) {
            logger.error("Ошибка доступа к данным при сохранении SearchIndex: {}", e.getMessage(), e);
            throw e; // Пробрасываем исключение, чтобы транзакция откатилась
        }
    }
}
