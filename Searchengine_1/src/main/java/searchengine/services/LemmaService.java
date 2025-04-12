package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import searchengine.model.Site;
import searchengine.repository.LemmaRepository;



@Service
@RequiredArgsConstructor
public class LemmaService {
    public static final Logger logger = LoggerFactory.getLogger(LemmaService.class);
    private final LemmaRepository lemmaRepository;

    @Transactional
    public void saveOrUpdateLemma(String lemmaText, Site site) {
        if (site == null) {
            logger.error("Ошибка: site == null при сохранении леммы '{}'", lemmaText);
            throw new IllegalArgumentException("Ошибка: site не может быть null при сохранении леммы");
        }

        try {
            lemmaRepository.upsertLemma(lemmaText, site.getId());
            logger.info("Лемма '{}' добавлена/обновлена для сайта '{}'", lemmaText, site.getUrl());
        } catch (Exception e) {
            logger.error("Ошибка при сохранении леммы '{}': {}", lemmaText, e.getMessage());
            throw new RuntimeException("Ошибка при сохранении леммы: " + e.getMessage());
        }
    }
}
