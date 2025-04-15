package searchengine.config;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class LuceneConfig {
    private static final Logger logger = LoggerFactory.getLogger(LuceneConfig.class);

    @Bean
    public LuceneMorphology russianLuceneMorphology() {
        try {
            LuceneMorphology morphology = new RussianLuceneMorphology(
            );
            logger.info("LuceneMorphology bean успешно создан");
            return morphology;
        } catch (IOException e) {
            logger.error("Ошибка создания LuceneMorphology", e);
            throw new RuntimeException("Инициализация морфологии завершилась ошибкой", e);
        }
    }
}