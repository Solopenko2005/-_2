package searchengine.services;

import org.springframework.stereotype.Service;
import searchengine.model.TopicGroup;

import java.util.HashSet;
import java.util.Set;

@Service
public class EnhancedTopicFilterService {

    private static final Set<String> STOP_TITLES = new HashSet<>() {{
        // Мусорные заголовки
        add("пресс релизы");
        add("пресс-релизы");
        add("title of the article");
        add("authors");
        add("pages");
        add("doi");
        add("issue");
        add("ranked assessment");
        add("efficiency of");
        add("forecasting");
        add("federal scientific centre");
        add("the journal");
        add("агроинвестор pro");
        add("все мероприятия по теме");
        add("партнерский материал");
        add("в режиме планового роста");
        add("задача нового центра");
        add("7 фактов которые влияют");
        add("его собственники не заинтересованы");
        add("компания ростсельмаш получила звание");
        add("россии удалось достигнуть пороговых значений");
        add("аналитики пересмотрели прогнозы");
        add("на волне экспорта");
        add("региональная и отраслевая экономика");
        add("проект федерального бюджета предполагает");
        add("как технологии позволяют осуществлять");
        add("доля контрафакта на отечественном рынке");
        add("развитие генетики ускоренным путем");
        add("компания август инвестирует");
        add("ростсельмаш привезет на агросалон");
        add("теплица 365 дней в году");
        add("пальмовый жмых в кормах");
        add("стратегия борьбы с тепловым стрессом");
        add("парниковый эффект vs сельское хозяйство");
        add("агрострахование может снизить риски");
        add("юнайтед индастриал открывает филиал");
        add("иван кабаев директор департамента");
        add("все включено почему производители");
        add("реализация зерна российскими сельхозорганизациями");
        add("16 30 18 00 вечерняя сессия");
        add("цифровые финансовые активы в апк");
    }};

    private static final Set<String> STOP_KEYWORDS = new HashSet<>() {{
        add("пресс релиз");
        add("пресс-релиз");
        add("doi:");
        add("pages");
        add("authors");
        add("issue");
        add("vol.");
        add("no.");
        add("abstract");
        add("keywords");
        add("introduction");
        add("methodology");
        add("results");
        add("conclusion");
        add("references");
        add("приложение");
        add("приложения");
    }};

    /**
     * Улучшенная фильтрация тем
     */
    public boolean isRelevantAndCleanTopic(TopicGroup group) {
        if (group == null) return false;

        String title = group.getTitle().toLowerCase();

        // Проверяем слишком длинные заголовки (вероятно содержат весь текст)
        if (title.length() > 200) {
            return false;
        }

        // Проверяем мусорные заголовки
        for (String stopTitle : STOP_TITLES) {
            if (title.contains(stopTitle.toLowerCase())) {
                return false;
            }
        }

        // Проверяем, содержит ли заголовок только цифры и спецсимволы
        if (title.matches("^[\\d\\s\\p{Punct}]+$")) {
            return false;
        }

        // Проверяем, не является ли это фрагментом контента
        if (isContentFragment(title)) {
            return false;
        }

        // Минимальная частота (но не слишком большая - это может быть артефакт)
        if (group.getFrequency() < 2 || group.getFrequency() > 100) {
            return false;
        }

        // Минимальное количество сайтов
        if (group.getSiteCount() < 2) {
            return false;
        }

        return true;
    }

    /**
     * Проверка, является ли текст фрагментом контента
     */
    private boolean isContentFragment(String text) {
        // Фрагменты контента часто содержат:
        // 1. Слишком много цифр и дат
        // 2. Длинные последовательности
        // 3. Технические данные

        String[] words = text.split("\\s+");

        // Слишком много слов (вероятно контент)
        if (words.length > 15) {
            return true;
        }

        // Считаем цифры и спецсимволы
        int digitCount = 0;
        int punctuationCount = 0;

        for (char c : text.toCharArray()) {
            if (Character.isDigit(c)) digitCount++;
            if (!Character.isLetterOrDigit(c) && c != ' ') punctuationCount++;
        }

        // Если слишком много цифр или спецсимволов
        if (digitCount > text.length() * 0.3 || punctuationCount > text.length() * 0.2) {
            return true;
        }

        // Проверяем на ключевые слова контента
        for (String keyword : STOP_KEYWORDS) {
            if (text.contains(keyword.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Очистить заголовок темы
     */
    public String cleanTopicTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            return "";
        }

        String cleaned = title.trim();

        // Удаляем технические префиксы
        cleaned = cleaned.replaceAll("^(пресс\\s*релиз|press\\s*release):?\\s*", "");

        // Удаляем даты в формате "29 декабря 2025"
        cleaned = cleaned.replaceAll("\\d{1,2}\\s+(января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)\\s+\\d{4}", "");

        // Удаляем просмотры
        cleaned = cleaned.replaceAll("просмотров\\s+\\d+", "");

        // Удаляем лишние пробелы
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        // Ограничиваем длину
        if (cleaned.length() > 100) {
            cleaned = cleaned.substring(0, 100) + "...";
        }

        return cleaned;
    }
}