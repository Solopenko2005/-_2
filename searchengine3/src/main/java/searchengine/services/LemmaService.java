package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.model.Lemma;
import searchengine.repository.LemmaRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
public class LemmaService {

    private final LemmaRepository lemmaRepository;

    @Autowired
    public LemmaService(LemmaRepository lemmaRepository) {
        this.lemmaRepository = lemmaRepository;
    }

    // Метод для обновления частот лемм в базе и возврата частоты
    public Map<String, Integer> updateLemmaFrequenciesAndReturn(Set<String> lemmas) {
        Map<String, Integer> lemmaFrequency = new HashMap<>();

        for (String lemma : lemmas) {
            Lemma existingLemma = lemmaRepository.findByLemma(lemma);
            if (existingLemma != null) {
                // Лемма уже существует, увеличиваем частоту
                existingLemma.setFrequency(existingLemma.getFrequency() + 1);
                lemmaRepository.save(existingLemma);
                lemmaFrequency.put(lemma, existingLemma.getFrequency());
            } else {
                // Лемма не найдена, создаем новую с частотой 1
                Lemma newLemma = new Lemma();
                newLemma.setLemma(lemma);
                newLemma.setFrequency(1);
                lemmaRepository.save(newLemma);
                lemmaFrequency.put(lemma, 1); // Частота новой леммы = 1
            }
        }

        return lemmaFrequency;
    }
}
