package searchengine.config;

import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MorphologyConfig {

    @Bean
    public RussianLuceneMorphology russianLuceneMorphology() throws Exception {
        return new RussianLuceneMorphology();
    }
}
