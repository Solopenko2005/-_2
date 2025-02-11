package searchengine.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Status;
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

    @Autowired
    public DatabaseService(SiteRepository siteRepository, PageRepository pageRepository, LemmaRepository lemmaRepository) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
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

    // Сохранение страницы
    public void savePage(Page page) {
        pageRepository.save(page);
        logger.info("Сохранена страница: {}", page.getPath());
    }

    /**
     * Сохранение леммы с защитой от дублирования.
     */
    @Transactional
    public void saveLemma(String lemmaText, Site site) {
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
            } else {
                // Создаем новую лемму
                Lemma newLemma = new Lemma(lemmaText, site, 1);
                lemmaRepository.save(newLemma);
                logger.info("Сохранена новая лемма '{}'", lemmaText);
            }
        } catch (DataAccessException e) {
            logger.error("Ошибка доступа к данным при сохранении леммы '{}': {}", lemmaText, e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Неизвестная ошибка при сохранении леммы '{}': {}", lemmaText, e.getMessage(), e);
        }
    }

    // Очистка базы данных перед новой индексацией
    public void clearAllData() {
        lemmaRepository.deleteAll();
        pageRepository.deleteAll();
        siteRepository.deleteAll();
        logger.info("База данных очищена перед новой индексацией.");
    }
}
