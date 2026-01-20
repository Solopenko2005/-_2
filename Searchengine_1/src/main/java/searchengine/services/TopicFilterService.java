package searchengine.services;

import org.springframework.stereotype.Service;
import searchengine.model.TopicGroup;

import java.util.HashSet;
import java.util.Set;

@Service
public class TopicFilterService {

    private static final Set<String> STOP_TITLES = new HashSet<>() {{
        add("download");
        add("логин");
        add("регистрация");
        add("куки");
        add("cookie");
        add("поиск");
        add("search");
        add("найти");
        add("find");
        add("контакты");
        add("contacts");
        add("о сайте");
        add("about");
        add("главная");
        add("home");
        add("новости");
        add("news");
        add("реквизиты");
        add("подписка");
        add("subscription");
        add("архив");
        add("archive");
        add("помощь");
        add("help");
        add("faq");
        add("вопросы");
        add("privacy");
        add("политика");
        add("правила");
        add("terms");
        add("условия");
        add("copyright");
        add("копирайт");
        add("авторские права");
    }};

    private static final Set<String> STOP_KEYWORDS = new HashSet<>() {{
        add("login");
        add("password");
        add("пароль");
        add("email");
        add("e-mail");
        add("cookie");
        add("download");
        add("скачать");
        add("регистрация");
        add("поиск");
        add("search");
        add("найти");
        add("menu");
        add("меню");
        add("header");
        add("footer");
        add("шапка");
        add("подвал");
        add("navigation");
        add("навигация");
        add("breadcrumb");
        add("хлебные крошки");
        add("sidebar");
        add("боковая панель");
    }};

    public boolean isRelevantTopic(TopicGroup group) {
        if (group == null) return false;

        String title = group.getTitle().toLowerCase();

        // Проверяем стоп-слова в заголовке
        for (String stopWord : STOP_TITLES) {
            if (title.contains(stopWord.toLowerCase())) {
                return false;
            }
        }

        // Проверяем слишком короткие или длинные заголовки
        if (title.length() < 3 || title.length() > 200) {
            return false;
        }

        // Проверяем стоп-слова в ключевых леммах
        if (group.getKeyLemmas() != null) {
            for (String lemma : group.getKeyLemmas()) {
                if (STOP_KEYWORDS.contains(lemma.toLowerCase())) {
                    return false;
                }
            }
        }

        // Минимальная частота
        if (group.getFrequency() < 2) {
            return false;
        }

        // Проверяем, не является ли тема технической (только цифры, спецсимволы)
        if (title.matches("^[\\d\\s\\p{Punct}]+$")) {
            return false;
        }

        return true;
    }
}