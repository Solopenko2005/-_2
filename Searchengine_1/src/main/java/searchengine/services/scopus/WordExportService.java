package searchengine.services.scopus;

import org.apache.poi.xwpf.usermodel.*;
import searchengine.model.scopus.ScientificArticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Сервис для экспорта результатов анализа в файлы Word (.docx)
 */
@Service
public class WordExportService {

    private static final Logger log = LoggerFactory.getLogger(WordExportService.class);

    /**
     * Создание первого файла: Темы + частота упоминания технологий
     * Формат: Таблица с колонками [Тема статьи, Технология, Частота упоминания]
     */
    public byte[] createTechnologiesAndTopicsFile(List<ScientificArticle> articles) {
        log.info("Creating Word file: Technologies and Topics");

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFDocument document = new XWPFDocument();

            // Заголовок документа
            createHeading(document, "Технологии в семеноводстве и селекции: Темы статей", 1);
            createParagraph(document, "Отчет содержит темы статей и частоту упоминания технологий в каждой статье.", false);
            createParagraph(document, "", false); // Пустая строка

            // Сбор всех технологий и их частот по статьям
            Map<String, Map<String, Integer>> technologyToArticles = new HashMap<>();

            for (ScientificArticle article : articles) {
                if (article.getExtractedTechnologies() != null && !article.getExtractedTechnologies().isEmpty()) {
                    String title = article.getTitle() != null ? article.getTitle() : "Без названия";

                    for (Map.Entry<String, Integer> entry : article.getExtractedTechnologies().entrySet()) {
                        String technology = entry.getKey();
                        int frequency = entry.getValue();

                        technologyToArticles.computeIfAbsent(technology, k -> new LinkedHashMap<>())
                                .put(title, frequency);
                    }
                }
            }

            // Сортировка технологий по общей частоте упоминания
            List<Map.Entry<String, Map<String, Integer>>> sortedTechnologies =
                    new ArrayList<>(technologyToArticles.entrySet());
            sortedTechnologies.sort((a, b) -> {
                int sumA = a.getValue().values().stream().mapToInt(Integer::intValue).sum();
                int sumB = b.getValue().values().stream().mapToInt(Integer::intValue).sum();
                return Integer.compare(sumB, sumA);
            });

            // Создание таблицы
            XWPFTable table = document.createTable();

            // Заголовок таблицы
            XWPFTableRow headerRow = table.getRow(0);
            setTableCellText(headerRow.getCell(0), "№", true);
            setTableCellText(headerRow.getCell(1), "Технология", true);
            setTableCellText(headerRow.getCell(2), "Общая частота", true);
            setTableCellText(headerRow.getCell(3), "Темы статей", true);

            // Добавление строк с технологиями
            int rowNum = 1;
            for (Map.Entry<String, Map<String, Integer>> techEntry : sortedTechnologies) {
                String technology = techEntry.getKey();
                Map<String, Integer> articleFreq = techEntry.getValue();

                int totalFrequency = articleFreq.values().stream().mapToInt(Integer::intValue).sum();
                String articlesList = String.join("; ", articleFreq.keySet());

                XWPFTableRow row = table.createRow();
                setTableCellText(row.getCell(0), String.valueOf(rowNum), false);
                setTableCellText(row.getCell(1), technology, false);
                setTableCellText(row.getCell(2), String.valueOf(totalFrequency), false);
                setTableCellText(row.getCell(3), articlesList, false);

                rowNum++;
            }

            // Статистика
            createParagraph(document, "", false);
            createHeading(document, "Статистика", 2);
            createParagraph(document, "Всего статей проанализировано: " + articles.size(), false);
            createParagraph(document, "Всего технологий выявлено: " + sortedTechnologies.size(), false);

            document.write(out);
            log.info("Successfully created Technologies and Topics file");
            return out.toByteArray();

        } catch (IOException e) {
            log.error("Error creating Word file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create Word document", e);
        }
    }

    /**
     * Создание второго файла: Аннотации, ключевые слова + частота упоминания
     * Формат: Подробная информация по каждой статье
     */
    public byte[] createAbstractsKeywordsFile(List<ScientificArticle> articles) {
        log.info("Creating Word file: Abstracts, Keywords and Technologies");

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFDocument document = new XWPFDocument();

            // Заголовок документа
            createHeading(document, "Технологии в семеноводстве и селекции: Аннотации и ключевые слова", 1);
            createParagraph(document, "Подробный отчет с аннотациями, ключевыми словами и выявленными технологиями.", false);
            createParagraph(document, "", false);

            // Группировка технологий по общей частоте
            Map<String, Integer> globalTechFrequency = new HashMap<>();
            for (ScientificArticle article : articles) {
                if (article.getExtractedTechnologies() != null) {
                    for (Map.Entry<String, Integer> entry : article.getExtractedTechnologies().entrySet()) {
                        globalTechFrequency.merge(entry.getKey(), entry.getValue(), Integer::sum);
                    }
                }
            }

            // Заголовок раздела с общей статистикой
            createHeading(document, "Общая частота упоминания технологий", 2);
            XWPFTable summaryTable = document.createTable();

            XWPFTableRow headerRow = summaryTable.getRow(0);
            setTableCellText(headerRow.getCell(0), "№", true);
            setTableCellText(headerRow.getCell(1), "Технология", true);
            setTableCellText(headerRow.getCell(2), "Частота упоминания", true);

            List<Map.Entry<String, Integer>> sortedTech = new ArrayList<>(globalTechFrequency.entrySet());
            sortedTech.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

            int rowNum = 1;
            for (Map.Entry<String, Integer> entry : sortedTech) {
                XWPFTableRow row = summaryTable.createRow();
                setTableCellText(row.getCell(0), String.valueOf(rowNum), false);
                setTableCellText(row.getCell(1), entry.getKey(), false);
                setTableCellText(row.getCell(2), String.valueOf(entry.getValue()), false);
                rowNum++;
            }

            // Детальная информация по статьям
            createParagraph(document, "", false);
            createHeading(document, "Детальная информация по статьям", 2);

            int articleNum = 1;
            for (ScientificArticle article : articles) {
                createHeading(document, "Статья #" + articleNum, 3);
                articleNum++;

                // Тема
                createParagraph(document, "Тема: " + (article.getTitle() != null ? article.getTitle() : "Не указана"), false);

                // Источник
                createParagraph(document, "Источник: " + article.getSourceName(), false);

                // DOI
                if (article.getDoi() != null && !article.getDoi().isEmpty()) {
                    createParagraph(document, "DOI: " + article.getDoi(), false);
                }

                // Год публикации
                if (article.getPublicationYear() != null) {
                    createParagraph(document, "Год публикации: " + article.getPublicationYear(), false);
                }

                // Авторы
                if (article.getAuthors() != null && !article.getAuthors().isEmpty()) {
                    createParagraph(document, "Авторы: " + String.join(", ", article.getAuthors()), false);
                }

                // Аннотация
                createParagraph(document, "Аннотация:", true);
                createParagraph(document, article.getAbstractText() != null ? article.getAbstractText() : "Не предоставлена", false);

                // Ключевые слова
                createParagraph(document, "Ключевые слова:", true);
                if (article.getKeywords() != null && !article.getKeywords().isEmpty()) {
                    createParagraph(document, String.join(", ", article.getKeywords()), false);
                } else {
                    createParagraph(document, "Не указаны", false);
                }

                // Выявленные технологии
                createParagraph(document, "Выявленные технологии:", true);
                if (article.getExtractedTechnologies() != null && !article.getExtractedTechnologies().isEmpty()) {
                    XWPFTable techTable = document.createTable();
                    XWPFTableRow techHeader = techTable.getRow(0);
                    setTableCellText(techHeader.getCell(0), "Технология", true);
                    setTableCellText(techHeader.getCell(1), "Частота", true);

                    List<Map.Entry<String, Integer>> sortedArticleTech =
                            new ArrayList<>(article.getExtractedTechnologies().entrySet());
                    sortedArticleTech.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

                    for (Map.Entry<String, Integer> entry : sortedArticleTech) {
                        XWPFTableRow techRow = techTable.createRow();
                        setTableCellText(techRow.getCell(0), entry.getKey(), false);
                        setTableCellText(techRow.getCell(1), String.valueOf(entry.getValue()), false);
                    }
                } else {
                    createParagraph(document, "Технологии не выявлены", false);
                }

                createParagraph(document, "", false); // Разделитель между статьями
                createParagraph(document, "---------------------------------------------------------------------", false);
                createParagraph(document, "", false);
            }

            document.write(out);
            log.info("Successfully created Abstracts and Keywords file");
            return out.toByteArray();

        } catch (IOException e) {
            log.error("Error creating Word file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create Word document", e);
        }
    }

    // Вспомогательные методы

    private void createHeading(XWPFDocument document, String text, int level) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.LEFT);

        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setBold(true);

        switch (level) {
            case 1:
                run.setFontSize(18);
                run.setFontFamily("Arial");
                break;
            case 2:
                run.setFontSize(16);
                run.setFontFamily("Arial");
                break;
            case 3:
                run.setFontSize(14);
                run.setFontFamily("Arial");
                break;
            default:
                run.setFontSize(12);
        }
    }

    private void createParagraph(XWPFDocument document, String text, boolean bold) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.LEFT);

        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setBold(bold);
        run.setFontSize(12);
    }

    private void setTableCellText(XWPFTableCell cell, String text, boolean bold) {
        cell.removeParagraph(0);
        XWPFParagraph paragraph = cell.addParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setBold(bold);
        run.setFontSize(11);
    }
}