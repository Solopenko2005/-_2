package searchengine.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class DatabaseService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository ;
    @Autowired
    public DatabaseService(SiteRepository siteRepository, PageRepository pageRepository, LemmaRepository lemmaRepository, IndexRepository indexRepository) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
    }

    // Сохранение сайта
    public void saveSite(Site site) {
        siteRepository.save(site);
        logger.info("Сохранен сайт: {}", site.getUrl());
    }

    // Обновление статуса сайта
    public void updateSiteStatus(Site site, String errorMessage) {
        site.setStatus(Status.FAILED);
        site.setStatusTime(LocalDateTime.now());
        site.setLastError(errorMessage);
        siteRepository.save(site);
        logger.error("Обновлен статус сайта '{}' с ошибкой: {}", site.getUrl(), errorMessage);
    }

    @Transactional
    public Page savePage(Page page) {
        return pageRepository.save(page);
    }

    /**
     * Сохранение леммы с защитой от дублирования.
     *
     * @return
     */
    @Transactional
    public Lemma saveLemma(String lemmaText, Site site) {
        try {
            logger.info("Попытка сохранить лемму '{}' для сайта '{}'", lemmaText, site.getUrl());

            // Ищем существующую лемму
            Optional<Lemma> existingLemma = lemmaRepository.findByLemmaAndSite(lemmaText, site);
            if (existingLemma.isPresent()) {
                // Если лемма уже есть, увеличиваем её частоту
                Lemma lemma = existingLemma.get();
                lemma.setFrequency(lemma.getFrequency() + 1);
                lemmaRepository.save(lemma);
                logger.info("Частота леммы '{}' увеличена до {}", lemmaText, lemma.getFrequency());
                return lemma; // Возвращаем существующую лемму
            } else {
                // Создаем новую лемму
                Lemma newLemma = new Lemma(lemmaText, site, 1);
                lemmaRepository.save(newLemma);
                logger.info("Сохранена новая лемма '{}'", lemmaText);
                return newLemma; // Возвращаем новую лемму
            }
        } catch (DataAccessException e) {
            logger.error("Ошибка доступа к данным при сохранении леммы '{}': {}", lemmaText, e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Неизвестная ошибка при сохранении леммы '{}': {}", lemmaText, e.getMessage(), e);
        }
        return null; // В случае ошибки возвращаем null
    }

    // Очистка базы данных перед новой индексацией
    public void clearAllData() {
        lemmaRepository.deleteAll();
        pageRepository.deleteAll();
        siteRepository.deleteAll();
        logger.info("База данных очищена перед новой индексацией.");
    }
    // Реализация метода для сохранения SearchIndex
    @Transactional
    public void saveSearchIndex(SearchIndex searchIndex) {
        try {
            indexRepository.save(searchIndex);
            logger.info("Сохранен SearchIndex для страницы {} и леммы {}",
                    searchIndex.getPage().getId(), searchIndex.getLemma().getId());
        } catch (DataAccessException e) {
            logger.error("Ошибка доступа к данным при сохранении SearchIndex: {}", e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Неизвестная ошибка при сохранении SearchIndex: {}", e.getMessage(), e);
        }
    }

    // Предположим, что DatabaseService также реализует метод поиска леммы
    public Optional<Lemma> findLemmaByTextAndSite(String lemmaText, Site site) {
        return lemmaRepository.findByLemmaAndSite(lemmaText, site);
    }

}
