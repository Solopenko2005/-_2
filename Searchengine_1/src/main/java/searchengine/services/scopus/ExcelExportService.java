package searchengine.services.scopus;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import searchengine.model.scopus.ScientificArticle;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ExcelExportService {

    /**
     * Создает Excel файл с темами и связанными статьями из Scopus
     */
    public byte[] createTopicsWithArticlesExcel(Map<String, List<ScientificArticle>> topicsWithArticles) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Breeding Technologies");

            // Создаем стили
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle topicStyle = createTopicStyle(workbook);
            CellStyle articleStyle = createArticleStyle(workbook);

            // Заголовки
            Row headerRow = sheet.createRow(0);
            String[] headers = {"№", "Technology Topic", "Article Title", "Authors", "Year", "DOI", "Scopus Link", "Abstract"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Заполняем данными
            int rowNum = 1;
            int topicNumber = 1;

            for (Map.Entry<String, List<ScientificArticle>> entry : topicsWithArticles.entrySet()) {
                String topic = entry.getKey();
                List<ScientificArticle> articles = entry.getValue();

                if (articles == null || articles.isEmpty()) {
                    // Если статей нет, добавляем строку с темой и сообщением
                    Row row = sheet.createRow(rowNum++);
                    Cell topicNumCell = row.createCell(0);
                    topicNumCell.setCellValue(topicNumber++);
                    topicNumCell.setCellStyle(topicStyle);

                    Cell topicCell = row.createCell(1);
                    topicCell.setCellValue(topic);
                    topicCell.setCellStyle(topicStyle);

                    Cell messageCell = row.createCell(2);
                    messageCell.setCellValue("No articles found");
                    messageCell.setCellStyle(articleStyle);
                } else {
                    // Для каждой статьи создаем отдельную строку
                    for (ScientificArticle article : articles) {
                        Row row = sheet.createRow(rowNum++);

                        // Номер темы (объединяем ячейки позже)
                        Cell topicNumCell = row.createCell(0);
                        topicNumCell.setCellValue(topicNumber);
                        topicNumCell.setCellStyle(topicStyle);

                        // Название темы
                        Cell topicCell = row.createCell(1);
                        topicCell.setCellValue(topic);
                        topicCell.setCellStyle(topicStyle);

                        // Название статьи
                        Cell titleCell = row.createCell(2);
                        titleCell.setCellValue(article.getTitle() != null ? article.getTitle() : "N/A");
                        titleCell.setCellStyle(articleStyle);

                        // Авторы
                        Cell authorsCell = row.createCell(3);
                        String authors = article.getAuthors() != null ?
                                String.join("; ", article.getAuthors()) : "N/A";
                        // Ограничиваем длину
                        if (authors.length() > 300) authors = authors.substring(0, 297) + "...";
                        authorsCell.setCellValue(authors);
                        authorsCell.setCellStyle(articleStyle);

                        // Год
                        Cell yearCell = row.createCell(4);
                        yearCell.setCellValue(article.getPublicationYear() != null ? article.getPublicationYear() : 0);
                        yearCell.setCellStyle(articleStyle);

                        // DOI
                        Cell doiCell = row.createCell(5);
                        doiCell.setCellValue(article.getDoi() != null ? article.getDoi() : "N/A");
                        doiCell.setCellStyle(articleStyle);

                        // Ссылка на Scopus
                        Cell linkCell = row.createCell(6);
                        String scopusLink = generateScopusLink(article);
                        linkCell.setCellValue(scopusLink);
                        linkCell.setCellStyle(articleStyle);

                        // Аннотация (ограничиваем длину)
                        Cell abstractCell = row.createCell(7);
                        String abstractText = article.getAbstractText() != null ? article.getAbstractText() : "";
                        if (abstractText.length() > 500) abstractText = abstractText.substring(0, 497) + "...";
                        abstractCell.setCellValue(abstractText);
                        abstractCell.setCellStyle(articleStyle);
                    }
                    topicNumber++;
                }
            }

            // Объединяем ячейки для тем с несколькими статьями
            mergeTopicCells(sheet, topicsWithArticles);

            // Авто-настройка ширины колонок
            autoSizeColumns(sheet);

            // Сохраняем в байтовый массив
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();

        } catch (Exception e) {
            log.error("Error creating Excel file: {}", e.getMessage(), e);
            return new byte[0];
        }
    }

    /**
     * Создает стиль для заголовков
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    /**
     * Создает стиль для названий тем
     */
    private CellStyle createTopicStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setWrapText(true);
        return style;
    }

    /**
     * Создает стиль для статей
     */
    private CellStyle createArticleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        return style;
    }

    /**
     * Генерирует ссылку на Scopus
     */
    private String generateScopusLink(ScientificArticle article) {
        if (article.getDoi() != null && !article.getDoi().isEmpty()) {
            return "https://doi.org/" + article.getDoi();
        } else if (article.getLink() != null && !article.getLink().isEmpty()) {
            return article.getLink();
        } else if (article.getSourceId() != null && !article.getSourceId().isEmpty()) {
            return "https://www.scopus.com/record/display.uri?eid=" + article.getSourceId();
        }
        return "N/A";
    }

    /**
     * Объединяет ячейки для заголовков тем с несколькими статьями
     */
    private void mergeTopicCells(Sheet sheet, Map<String, List<ScientificArticle>> topicsWithArticles) {
        int currentRow = 1;

        for (Map.Entry<String, List<ScientificArticle>> entry : topicsWithArticles.entrySet()) {
            List<ScientificArticle> articles = entry.getValue();
            int articleCount = (articles != null && !articles.isEmpty()) ? articles.size() : 1;

            if (articleCount > 1) {
                // Объединяем ячейки с номером темы
                CellRangeAddress topicNumRange = new CellRangeAddress(currentRow, currentRow + articleCount - 1, 0, 0);
                sheet.addMergedRegion(topicNumRange);

                // Объединяем ячейки с названием темы
                CellRangeAddress topicRange = new CellRangeAddress(currentRow, currentRow + articleCount - 1, 1, 1);
                sheet.addMergedRegion(topicRange);
            }

            currentRow += articleCount;
        }
    }

    /**
     * Автоматическая настройка ширины колонок
     */
    private void autoSizeColumns(Sheet sheet) {
        for (int i = 0; i < 8; i++) {
            sheet.autoSizeColumn(i);
            // Ограничиваем максимальную ширину
            int width = sheet.getColumnWidth(i);
            if (width > 20000) {
                sheet.setColumnWidth(i, 20000);
            }
            if (width < 4000) {
                sheet.setColumnWidth(i, 4000);
            }
        }
    }
    /**
     * Создает Excel файл с темами, годами и статьями
     */
    public byte[] createTopicsWithYearsExcel(Map<String, Map<Integer, List<ScientificArticle>>> topicsData) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Breeding Technologies 2024-2026");

            // Стили
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle topicStyle = createTopicStyle(workbook);
            CellStyle yearStyle = createYearStyle(workbook);
            CellStyle articleStyle = createArticleStyle(workbook);

            // Заголовки
            Row headerRow = sheet.createRow(0);
            String[] headers = {"№", "Technology Topic", "Year", "Article Title", "Authors", "DOI", "Scopus Link", "Abstract"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            int topicNumber = 1;

            for (Map.Entry<String, Map<Integer, List<ScientificArticle>>> topicEntry : topicsData.entrySet()) {
                String topic = topicEntry.getKey();
                Map<Integer, List<ScientificArticle>> yearArticles = topicEntry.getValue();

                boolean firstRow = true;
                int articlesCount = 0;

                for (Map.Entry<Integer, List<ScientificArticle>> yearEntry : yearArticles.entrySet()) {
                    Integer year = yearEntry.getKey();
                    List<ScientificArticle> articles = yearEntry.getValue();
                    articlesCount += articles.size();

                    if (articles.isEmpty()) {
                        // Если нет статей за год, добавляем строку с сообщением
                        Row row = sheet.createRow(rowNum++);
                        if (firstRow) {
                            row.createCell(0).setCellValue(topicNumber);
                            row.createCell(1).setCellValue(topic);
                            firstRow = false;
                        }
                        row.createCell(2).setCellValue(year);
                        row.createCell(3).setCellValue("No articles found for " + year);
                    } else {
                        // Добавляем статьи за год
                        for (ScientificArticle article : articles) {
                            Row row = sheet.createRow(rowNum++);

                            // Номер темы и название (только для первой статьи темы)
                            if (firstRow) {
                                row.createCell(0).setCellValue(topicNumber);
                                row.createCell(1).setCellValue(topic);
                                firstRow = false;
                            }

                            // Год
                            row.createCell(2).setCellValue(year);

                            // Название статьи
                            row.createCell(3).setCellValue(article.getTitle() != null ? article.getTitle() : "N/A");

                            // Авторы
                            String authors = article.getAuthors() != null ?
                                    String.join("; ", article.getAuthors()) : "N/A";
                            if (authors.length() > 300) authors = authors.substring(0, 297) + "...";
                            row.createCell(4).setCellValue(authors);

                            // DOI
                            row.createCell(5).setCellValue(article.getDoi() != null ? article.getDoi() : "N/A");

                            // Ссылка
                            String link = article.getDoi() != null ?
                                    "https://doi.org/" + article.getDoi() : "N/A";
                            row.createCell(6).setCellValue(link);

                            // Аннотация
                            String abstractText = article.getAbstractText() != null ?
                                    article.getAbstractText() : "";
                            if (abstractText.length() > 500) abstractText = abstractText.substring(0, 497) + "...";
                            row.createCell(7).setCellValue(abstractText);
                        }
                    }
                }

                // Объединяем ячейки для тем с несколькими строками
                if (articlesCount > 0) {
                    int startRow = rowNum - articlesCount;
                    if (startRow > 0) {
                        sheet.addMergedRegion(new CellRangeAddress(startRow, rowNum - 1, 0, 0));
                        sheet.addMergedRegion(new CellRangeAddress(startRow, rowNum - 1, 1, 1));
                    }
                }

                topicNumber++;
            }

            // Авто-настройка ширины
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                int width = sheet.getColumnWidth(i);
                if (width > 20000) sheet.setColumnWidth(i, 20000);
                if (width < 4000) sheet.setColumnWidth(i, 4000);
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();

        } catch (Exception e) {
            log.error("Error creating Excel file: {}", e.getMessage(), e);
            return new byte[0];
        }
    }

    private CellStyle createYearStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }
}