package searchengine.services;

import searchengine.model.Page;
import searchengine.model.Topic;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import searchengine.repository.TopicRepository;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static searchengine.services.LemmaService.logger;

@Service
public class TopicExtractorService {

    // Паттерн для поиска заголовков в стилях span
    private static final Pattern HEADING_STYLE_PATTERN = Pattern.compile(
            "font-size:(9\\.0|10\\.0|11\\.0|12\\.0|14\\.0|16\\.0|18\\.0|20\\.0|24\\.0)pt",
            Pattern.CASE_INSENSITIVE
    );

    // Паттерн для поиска цветов, которые могут указывать на заголовки
    private static final Pattern HEADING_COLOR_PATTERN = Pattern.compile(
            "color:(red|#ff0000|#c00000|#990000|blue|#0000ff|#2e74b5|darkblue|green|#00b050|#31869b)",
            Pattern.CASE_INSENSITIVE
    );

    // Паттерн для поиска жирного текста
    private static final Pattern BOLD_STYLE_PATTERN = Pattern.compile(
            "font-weight:(bold|700|800|900)",
            Pattern.CASE_INSENSITIVE
    );
    private TopicRepository topicRepository;

    public List<Topic> extractTopics(Page page, String htmlContent) {
        List<Topic> topics = new ArrayList<>();

        try {
            Document doc = Jsoup.parse(htmlContent);

            // Удаляем ненужные элементы
            doc.select("script, style, meta, link, noscript").remove();

            // Стратегия 1: Извлечение по стандартным HTML-заголовкам (h1-h6)
            topics.addAll(extractByStandardHeadings(page, doc));

            // Стратегия 2: Извлечение по стилизованным span-заголовкам (ваш случай)
            topics.addAll(extractByStyledSpans(page, doc));

            // Стратегия 3: Извлечение по таблицам (если есть)
            topics.addAll(extractByTables(page, doc));

            // Стратегия 4: Извлечение по div с определенными классами
            topics.addAll(extractByDivClasses(page, doc));

            // Очищаем и нормализуем темы
            topics = cleanAndDeduplicateTopics(topics);

            // Устанавливаем порядок
            for (int i = 0; i < topics.size(); i++) {
                topics.get(i).setOrderNum(i);
            }

        } catch (Exception e) {
            System.err.println("Ошибка при извлечении тем для страницы " + page.getPath() + ": " + e.getMessage());
        }

        return topics;
    }

    // Извлечение по стандартным HTML-заголовкам
    private List<Topic> extractByStandardHeadings(Page page, Document doc) {
        List<Topic> topics = new ArrayList<>();

        // Ищем заголовки h1-h6
        Elements headings = doc.select("h1, h2, h3, h4, h5, h6");

        for (Element heading : headings) {
            String title = heading.text().trim();
            if (title.isEmpty() || title.length() > 500) continue;

            // Ищем контент для этого заголовка
            String content = extractContentForHeading(heading);
            if (content.length() < 30) continue; // Минимальная длина контента

            Topic topic = createTopic(page, title, content);
            topics.add(topic);
        }

        return topics;
    }

    // Извлечение по стилизованным span-заголовкам (ваш случай)
    private List<Topic> extractByStyledSpans(Page page, Document doc) {
        List<Topic> topics = new ArrayList<>();

        // Ищем все span элементы
        Elements spans = doc.select("span");

        for (Element span : spans) {
            String style = span.attr("style");
            if (style.isEmpty()) continue;

            String text = span.text().trim();
            if (text.isEmpty() || text.length() > 500) continue;

            // Проверяем, является ли span заголовком по стилю
            if (isStyledSpanHeading(style, text)) {
                String title = cleanSpanText(text);

                // Ищем контент для этого заголовка
                String content = extractContentForStyledSpan(span);
                if (content.length() < 30) continue;

                Topic topic = createTopic(page, title, content);
                topics.add(topic);
            }
        }

        return topics;
    }

    // Проверка, является ли span заголовком по стилю
    private boolean isStyledSpanHeading(String style, String text) {
        // Проверяем размер шрифта (обычно заголовки больше основного текста)
        if (HEADING_STYLE_PATTERN.matcher(style).find()) {
            // Дополнительные проверки для уменьшения ложных срабатываний
            return text.length() <= 200 && // Заголовки обычно короче
                    !text.matches("^[\\d\\s\\p{Punct}]+$") && // Не только цифры и пунктуация
                    !text.toLowerCase().contains("copyright") && // Не копирайт
                    !text.matches("^страница\\s+\\d+$"); // Не номера страниц
        }

        // Проверяем цвет (красный, синий часто используются для заголовков)
        if (HEADING_COLOR_PATTERN.matcher(style).find()) {
            return text.length() <= 200;
        }

        // Проверяем жирный шрифт
        if (BOLD_STYLE_PATTERN.matcher(style).find()) {
            // Жирный текст + короткая длина = возможный заголовок
            return text.length() <= 150 && text.split("\\s+").length <= 10;
        }

        return false;
    }

    // Очистка текста из span
    private String cleanSpanText(String text) {
        // Удаляем лишние пробелы и переносы строк
        text = text.replaceAll("\\s+", " ")
                .replaceAll("[\\r\\n]+", " ")
                .trim();

        // Удаляем возможные HTML-сущности
        text = text.replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"");

        return text;
    }

    // Извлечение контента для стилизованного span
    private String extractContentForStyledSpan(Element span) {
        StringBuilder content = new StringBuilder();

        // Начинаем со следующего элемента после span
        Element current = span.nextElementSibling();
        int depth = 0;

        while (current != null && depth < 10) { // Ограничиваем глубину поиска
            String tagName = current.tagName().toLowerCase();

            // Если встретили следующий возможный заголовок - останавливаемся
            if (isPotentialHeading(current)) {
                break;
            }

            // Добавляем текст элемента
            if (isContentElement(current)) {
                String elementText = current.text().trim();
                if (!elementText.isEmpty()) {
                    if (content.length() > 0) content.append(" ");
                    content.append(elementText);
                }
            }

            current = current.nextElementSibling();
            depth++;
        }

        return content.toString();
    }

    // Извлечение контента для стандартного заголовка
    private String extractContentForHeading(Element heading) {
        StringBuilder content = new StringBuilder();

        Element current = heading.nextElementSibling();
        while (current != null) {
            String tagName = current.tagName().toLowerCase();

            // Если встретили следующий заголовок - останавливаемся
            if (tagName.matches("^h[1-6]$")) {
                break;
            }

            // Если встретили возможный стилизованный заголовок - останавливаемся
            if (isPotentialHeading(current)) {
                break;
            }

            // Добавляем текст элемента
            if (isContentElement(current)) {
                String elementText = current.text().trim();
                if (!elementText.isEmpty()) {
                    if (content.length() > 0) content.append(" ");
                    content.append(elementText);
                }
            }

            current = current.nextElementSibling();
        }

        return content.toString();
    }

    // Извлечение тем из таблиц
    private List<Topic> extractByTables(Page page, Document doc) {
        List<Topic> topics = new ArrayList<>();

        Elements tables = doc.select("table");

        for (Element table : tables) {
            // Ищем заголовок таблицы
            String title = extractTableTitle(table);
            if (title.isEmpty()) continue;

            // Извлекаем содержание таблицы
            String content = extractTableContent(table);
            if (content.length() < 20) continue;

            Topic topic = createTopic(page, title, content);
            topics.add(topic);
        }

        return topics;
    }

    // Извлечение заголовка таблицы
    private String extractTableTitle(Element table) {
        // Проверяем caption
        Element caption = table.selectFirst("caption");
        if (caption != null) {
            String text = caption.text().trim();
            if (!text.isEmpty()) return text;
        }

        // Проверяем первую строку таблицы (часто это заголовок)
        Element firstRow = table.selectFirst("tr");
        if (firstRow != null) {
            String rowText = firstRow.text().trim();
            if (rowText.length() <= 200 && !rowText.matches(".*\\d{4}.*")) {
                return rowText;
            }
        }

        // Проверяем th элементы
        Elements thElements = table.select("th");
        if (!thElements.isEmpty()) {
            StringBuilder titleBuilder = new StringBuilder();
            for (Element th : thElements) {
                String thText = th.text().trim();
                if (!thText.isEmpty()) {
                    if (titleBuilder.length() > 0) titleBuilder.append(" - ");
                    titleBuilder.append(thText);
                }
            }
            if (titleBuilder.length() > 0) {
                return titleBuilder.toString();
            }
        }

        return "";
    }

    // Извлечение содержания таблицы
    private String extractTableContent(Element table) {
        StringBuilder content = new StringBuilder();

        // Извлекаем данные из td элементов
        Elements tdElements = table.select("td");
        for (Element td : tdElements) {
            String text = td.text().trim();
            if (!text.isEmpty() && !text.matches("^[\\d\\s\\p{Punct}]+$")) {
                if (content.length() > 0) content.append(" ");
                content.append(text);
            }
        }

        return content.toString();
    }

    // Извлечение по div с определенными классами
    private List<Topic> extractByDivClasses(Page page, Document doc) {
        List<Topic> topics = new ArrayList<>();

        // Ищем div с классами, которые могут указывать на заголовки/разделы
        String[] headingClasses = {"title", "heading", "header", "caption",
                "topic", "section", "chapter", "part"};

        for (String className : headingClasses) {
            Elements divs = doc.select("div[class*=" + className + "], div[id*=" + className + "]");

            for (Element div : divs) {
                String title = div.text().trim();
                if (title.isEmpty() || title.length() > 300) continue;

                // Ищем контент после этого div
                String content = extractContentAfterDiv(div);
                if (content.length() < 30) continue;

                Topic topic = createTopic(page, title, content);
                topics.add(topic);
            }
        }

        return topics;
    }

    // Проверка, является ли элемент возможным заголовком
    private boolean isPotentialHeading(Element element) {
        String tagName = element.tagName().toLowerCase();

        // Стандартные заголовки
        if (tagName.matches("^h[1-6]$")) {
            return true;
        }

        // Стилизованные span
        if (tagName.equals("span")) {
            String style = element.attr("style");
            if (!style.isEmpty()) {
                return isStyledSpanHeading(style, element.text());
            }
        }

        // Bold элементы
        if (tagName.equals("b") || tagName.equals("strong")) {
            String text = element.text().trim();
            return text.length() <= 200;
        }

        // Div с заголовочными классами
        if (tagName.equals("div")) {
            String className = element.attr("class").toLowerCase();
            String id = element.attr("id").toLowerCase();
            return className.contains("title") || className.contains("heading") ||
                    className.contains("header") || className.contains("caption") ||
                    id.contains("title") || id.contains("heading");
        }

        return false;
    }

    // Проверка, является ли элемент содержательным
    private boolean isContentElement(Element element) {
        String tagName = element.tagName().toLowerCase();

        return tagName.equals("p") ||
                tagName.equals("div") ||
                tagName.equals("span") ||
                tagName.equals("td") ||
                tagName.equals("li") ||
                (tagName.equals("table") && element.select("td").size() > 0);
    }

    // Извлечение контента после div
    private String extractContentAfterDiv(Element div) {
        StringBuilder content = new StringBuilder();

        Element current = div.nextElementSibling();
        while (current != null) {
            // Если встретили следующий заголовок - останавливаемся
            if (isPotentialHeading(current)) {
                break;
            }

            // Добавляем текст элемента
            if (isContentElement(current)) {
                String elementText = current.text().trim();
                if (!elementText.isEmpty()) {
                    if (content.length() > 0) content.append(" ");
                    content.append(elementText);
                }
            }

            current = current.nextElementSibling();
        }

        return content.toString();
    }

    // Создание объекта Topic
    private Topic createTopic(Page page, String title, String content) {
        Topic topic = new Topic();
        topic.setPage(page);
        topic.setSite(page.getSite());
        topic.setTitle(normalizeText(title));
        topic.setContent(normalizeText(content));
        topic.setLemmaCount(countWords(content));
        return topic;
    }

    // Нормализация текста
    private String normalizeText(String text) {
        if (text == null) return "";

        // Удаляем лишние пробелы и переносы строк
        text = text.replaceAll("\\s+", " ")
                .replaceAll("[\\r\\n]+", " ")
                .trim();

        // Удаляем HTML-теги, которые могли остаться
        text = text.replaceAll("<[^>]+>", "");

        // Ограничиваем длину
        if (text.length() > 2000) {
            text = text.substring(0, 2000) + "...";
        }

        return text;
    }

    // Подсчет слов
    private int countWords(String text) {
        if (text == null || text.trim().isEmpty()) return 0;
        return text.trim().split("\\s+").length;
    }

    // Очистка и дедупликация тем
    private List<Topic> cleanAndDeduplicateTopics(List<Topic> topics) {
        List<Topic> cleanedTopics = new ArrayList<>();
        List<String> seenTitles = new ArrayList<>();

        for (Topic topic : topics) {
            String title = topic.getTitle();
            String content = topic.getContent();

            // Пропускаем слишком короткие или пустые темы
            if (title.length() < 3 || content.length() < 20) {
                continue;
            }

            // Пропускаем дубликаты
            if (seenTitles.contains(title)) {
                continue;
            }

            // Пропускаем темы, где заголовок слишком похож на контент
            if (content.startsWith(title) && content.length() - title.length() < 50) {
                continue;
            }

            seenTitles.add(title);
            cleanedTopics.add(topic);
        }

        return cleanedTopics;
    }

    // Метод для тестирования извлечения из конкретного HTML
    public List<String> testExtraction(String html) {
        List<String> results = new ArrayList<>();

        try {
            Document doc = Jsoup.parse(html);

            // Тест 1: Стандартные заголовки
            Elements headings = doc.select("h1, h2, h3");
            for (Element h : headings) {
                results.add("Заголовок: " + h.tagName() + " - " + h.text());
            }

            // Тест 2: Стилизованные span
            Elements spans = doc.select("span[style]");
            for (Element span : spans) {
                String style = span.attr("style");
                String text = span.text().trim();

                if (!text.isEmpty() && isStyledSpanHeading(style, text)) {
                    results.add("Стилизованный заголовок: " + text);
                    results.add("  Стиль: " + style);
                }
            }

        } catch (Exception e) {
            results.add("Ошибка: " + e.getMessage());
        }

        return results;
    }
}