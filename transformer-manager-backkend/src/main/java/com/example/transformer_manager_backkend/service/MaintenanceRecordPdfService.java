package com.example.transformer_manager_backkend.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.transformer_manager_backkend.entity.AnalysisJob;
import com.example.transformer_manager_backkend.entity.Annotation;
import com.example.transformer_manager_backkend.entity.AnnotationBox;
import com.example.transformer_manager_backkend.entity.Image;
import com.example.transformer_manager_backkend.entity.Inspection;
import com.example.transformer_manager_backkend.entity.MaintenanceRecord;
import com.example.transformer_manager_backkend.entity.TransformerRecord;
import com.example.transformer_manager_backkend.repository.MaintenanceRecordRepository;
import com.example.transformer_manager_backkend.service.AnnotationService;
import com.lowagie.text.BadElementException;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.ListItem;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

//MaintenanceRecordPdfService
@Service
public class MaintenanceRecordPdfService {

    private static final String NOT_AVAILABLE = "N/A";
    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
    private static final Font SECTION_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
    private static final Font BODY_FONT = FontFactory.getFont(FontFactory.HELVETICA, 11);
    private static final Font CELL_HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
    private static final Font CELL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font SUMMARY_LABEL_FONT;
    private static final Font SUMMARY_VALUE_FONT;
    private static final Font CAPTION_FONT;
    private static final Font BRAND_TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20);
    private static final Font BRAND_SUBTITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA, 12);
    private static final Color BRAND_PRIMARY = new Color(25, 90, 165);
    private static final Color BRAND_SECONDARY = new Color(52, 152, 219);
    private static final Color LINE_COLOR = new Color(210, 220, 235);
    private static final Color IMAGE_FRAME_BG = new Color(245, 248, 252);
    private static final Color IMAGE_FRAME_BORDER = new Color(210, 220, 235);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        SUMMARY_LABEL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.BOLD);
        SUMMARY_LABEL_FONT.setColor(Color.WHITE);

        SUMMARY_VALUE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15);
        SUMMARY_VALUE_FONT.setColor(Color.WHITE);

        CAPTION_FONT = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9);
        CAPTION_FONT.setColor(Color.DARK_GRAY);
    }

    private final MaintenanceRecordRepository maintenanceRecordRepository;
    private final AnnotationService annotationService;
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm", Locale.ENGLISH);

    @Value("${upload.directory}")
    private String uploadDirectory;

    public MaintenanceRecordPdfService(MaintenanceRecordRepository maintenanceRecordRepository,
            AnnotationService annotationService) {
        this.maintenanceRecordRepository = maintenanceRecordRepository;
        this.annotationService = annotationService;
    }

    @Transactional(readOnly = true)
    public byte[] generatePdf(Long recordId) {
        long safeId = Optional.ofNullable(recordId)
            .orElseThrow(() -> new IllegalArgumentException("Record ID must be provided"));

        MaintenanceRecord record = maintenanceRecordRepository.findById(safeId)
                .orElseThrow(() -> new RuntimeException("Maintenance record not found"));

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            @SuppressWarnings("resource")
            Document document = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            document.open();

            addBrandingHeader(document, record);
            addHeader(document, record);
            addSummarySection(document, record);
            addTransformerSection(document, record);
            addElectricalSection(document, record);
            addMaintenanceSection(document, record);
            List<Image> maintenanceImages = Optional.ofNullable(record.getInspection())
                    .map(Inspection::getImages)
                    .orElse(List.of())
                    .stream()
                    .filter(img -> img != null && "Maintenance".equalsIgnoreCase(img.getType()))
                    .toList();
            Map<Long, String> maintenanceLabelMap = buildMaintenanceLabelMap(maintenanceImages);
            List<Path> baselineImages = collectBaselineImages(record);
            List<Path> maintenanceAnnotatedImages = collectMaintenanceAnnotatedImages(maintenanceImages);

            if (!baselineImages.isEmpty() || !maintenanceAnnotatedImages.isEmpty()) {
                addImageSection(document, "Baseline Images", baselineImages, "baseline");
                addImageSection(document, "Maintenance / Annotated Images", maintenanceAnnotatedImages, "maintenance");
                document.newPage();
            }

            addAnalysisSummary(document, record, maintenanceLabelMap);
            addAnnotationHistoryTable(document, maintenanceImages, maintenanceLabelMap);

            document.close();
            return baos.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new RuntimeException("Failed to generate maintenance record PDF", e);
        }
    }

    private void addHeader(Document document, MaintenanceRecord record) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[] { 1, 1 });
        table.setWidthPercentage(100);
        table.setSpacingAfter(10f);

        PdfPCell titleCell = new PdfPCell();
        titleCell.setBackgroundColor(new Color(245, 248, 252));
        titleCell.setPadding(10f);
        titleCell.setBorderColor(LINE_COLOR);
        Paragraph title = new Paragraph("Maintenance Record #" + record.getId(), TITLE_FONT);
        title.setSpacingAfter(4f);
        Paragraph subtitle = new Paragraph("ThermoSight Transformer Management System", BRAND_SUBTITLE_FONT);
        subtitle.setSpacingAfter(2f);
        titleCell.addElement(title);
        titleCell.addElement(subtitle);
        table.addCell(titleCell);

        PdfPCell metaCell = new PdfPCell();
        metaCell.setPadding(10f);
        metaCell.setBorderColor(LINE_COLOR);
        Paragraph created = new Paragraph("Created: " + formatDate(record.getCreatedAt()), CELL_FONT);
        created.setSpacingAfter(3f);
        Paragraph status = new Paragraph("Status: " + safeValue(record.getRecordStatus()), CELL_FONT);
        status.setSpacingAfter(3f);
        Paragraph inspector = new Paragraph("Inspector: " + safeValue(record.getInspectorName()), CELL_FONT);
        inspector.setSpacingAfter(3f);
        Paragraph inspectionDate = new Paragraph(
                "Inspection Date: " + formatDate(Optional.ofNullable(record.getInspection()).map(Inspection::getInspectionDate).orElse(null)),
                CELL_FONT);
        metaCell.addElement(created);
        metaCell.addElement(status);
        metaCell.addElement(inspector);
        metaCell.addElement(inspectionDate);
        table.addCell(metaCell);

        document.add(table);
    }

    private void addSummarySection(Document document, MaintenanceRecord record) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[] { 1, 1, 1 });
        table.setWidthPercentage(100);
        table.setSpacingAfter(12f);

        table.addCell(createSummaryCell("Record Status", safeValue(record.getRecordStatus()), BRAND_PRIMARY));
        table.addCell(createSummaryCell("Priority", safeValue(record.getMaintenancePriority()), BRAND_SECONDARY));

        String followUp = Boolean.TRUE.equals(record.getRequiresFollowUp())
                ? formatDate(record.getFollowUpDate())
                : "No follow-up scheduled";
        table.addCell(createSummaryCell("Follow-up", followUp, new Color(176, 94, 27)));

        document.add(table);
    }

    private void addTransformerSection(Document document, MaintenanceRecord record) throws DocumentException {
        document.add(makeSectionHeader("Transformer Details"));
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        TransformerRecord transformer = Optional.ofNullable(record.getInspection())
                .map(Inspection::getTransformerRecord)
                .orElse(null);
        addKeyValueCell(table, "Name", transformer != null ? safeValue(transformer.getName()) : NOT_AVAILABLE);
        addKeyValueCell(table, "Location", transformer != null ? safeValue(transformer.getLocationName()) : NOT_AVAILABLE);
        addKeyValueCell(table, "Capacity (kVA)", transformer != null ? formatNumber(transformer.getCapacity()) : NOT_AVAILABLE);
        addKeyValueCell(table, "Type", transformer != null ? safeValue(transformer.getTransformerType()) : NOT_AVAILABLE);
        addKeyValueCell(table, "Pole No.", transformer != null ? safeValue(transformer.getPoleNo()) : NOT_AVAILABLE);
        addKeyValueCell(table, "Coordinates", transformer != null ? formatCoordinates(transformer) : NOT_AVAILABLE);
        table.setSpacingAfter(12f);
        document.add(table);
    }

    private void addElectricalSection(Document document, MaintenanceRecord record) throws DocumentException {
        document.add(makeSectionHeader("Electrical Measurements"));
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        addKeyValueCell(table, "Voltage Phase A", formatNumber(record.getVoltagePhaseA(), " V"));
        addKeyValueCell(table, "Voltage Phase B", formatNumber(record.getVoltagePhaseB(), " V"));
        addKeyValueCell(table, "Voltage Phase C", formatNumber(record.getVoltagePhaseC(), " V"));
        addKeyValueCell(table, "Current Phase A", formatNumber(record.getCurrentPhaseA(), " A"));
        addKeyValueCell(table, "Current Phase B", formatNumber(record.getCurrentPhaseB(), " A"));
        addKeyValueCell(table, "Current Phase C", formatNumber(record.getCurrentPhaseC(), " A"));
        addKeyValueCell(table, "Power Factor", formatNumber(record.getPowerFactor()));
        addKeyValueCell(table, "Frequency", formatNumber(record.getFrequency(), " Hz"));
        addKeyValueCell(table, "Ambient Temp", formatNumber(record.getAmbientTemperature(), " °C"));
        addKeyValueCell(table, "Oil Temp", formatNumber(record.getOilTemperature(), " °C"));
        addKeyValueCell(table, "Winding Temp", formatNumber(record.getWindingTemperature(), " °C"));
        table.setSpacingAfter(12f);
        document.add(table);
    }

    private void addMaintenanceSection(Document document, MaintenanceRecord record) throws DocumentException {
        document.add(makeSectionHeader("Maintenance Notes"));
        addParagraph(document, "Transformer Status", record.getTransformerStatus());
        addListParagraph(document, "Detected Anomalies", record.getDetectedAnomalies());
        addListParagraph(document, "Corrective Actions", record.getCorrectiveActions());
        addParagraph(document, "Recommended Action", record.getRecommendedAction());
        addParagraph(document, "Engineer Notes", record.getEngineerNotes());
        addParagraph(document, "Additional Remarks", record.getAdditionalRemarks());
    }

    private void addBrandingHeader(Document document, MaintenanceRecord record) throws DocumentException {
        PdfPTable banner = new PdfPTable(new float[] { 3, 1 });
        banner.setWidthPercentage(100);
        banner.setSpacingAfter(10f);

        PdfPCell left = new PdfPCell();
        left.setPadding(12f);
        left.setBorderColor(LINE_COLOR);
        left.setBackgroundColor(new Color(241, 246, 252));
        Paragraph brand = new Paragraph("ThermoSight Transformer Management System", BRAND_TITLE_FONT);
        brand.setSpacingAfter(4f);
        Paragraph tagline = new Paragraph("Maintenance & Analysis Report", BRAND_SUBTITLE_FONT);
        left.addElement(brand);
        left.addElement(tagline);
        banner.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setPadding(12f);
        right.setHorizontalAlignment(Rectangle.ALIGN_RIGHT);
        right.setVerticalAlignment(Rectangle.ALIGN_MIDDLE);
        right.setBorderColor(LINE_COLOR);
        right.setBackgroundColor(new Color(250, 252, 255));
        Paragraph recordTag = new Paragraph("Record #" + record.getId(), TITLE_FONT);
        recordTag.setAlignment(Rectangle.ALIGN_RIGHT);
        Paragraph dateTag = new Paragraph("Generated: " + formatDate(java.time.LocalDateTime.now()), CELL_FONT);
        dateTag.setAlignment(Rectangle.ALIGN_RIGHT);
        right.addElement(recordTag);
        right.addElement(dateTag);
        banner.addCell(right);

        document.add(banner);
    }

    private Map<Long, String> buildMaintenanceLabelMap(List<Image> maintenanceImages) {
        Map<Long, String> map = new LinkedHashMap<>();
        int index = 1;
        for (Image image : maintenanceImages) {
            if (image != null && image.getId() != null) {
                map.put(image.getId(), "maintenance_" + index);
                index++;
            }
        }
        return map;
    }

    private void addAnalysisSummary(Document document, MaintenanceRecord record, Map<Long, String> maintenanceLabelMap) throws DocumentException {
        List<Image> maintenanceImages = Optional.ofNullable(record.getInspection())
                .map(Inspection::getImages)
                .orElse(List.of())
                .stream()
                .filter(img -> img != null && "Maintenance".equalsIgnoreCase(img.getType()))
                .toList();

        boolean hasAnalysis = maintenanceImages.stream().anyMatch(img -> img.getAnalysisJob() != null);
        document.add(makeSectionHeader("Anomaly Analysis"));
        if (!hasAnalysis) {
            document.add(new Paragraph("No anomaly analysis results available.", BODY_FONT));
            document.add(new Paragraph(" ")); // spacer
            return;
        }

        PdfPTable table = new PdfPTable(new float[] { 1.1f, 0.8f, 2f, 2.2f });
        table.setWidthPercentage(100);
        addHeaderCell(table, "Image");
        addHeaderCell(table, "Status");
        addHeaderCell(table, "Anomalies (type & confidence)");
        addHeaderCell(table, "Annotations / History");

        for (Image image : maintenanceImages) {
            AnalysisJob job = image.getAnalysisJob();
            if (job == null) {
                continue;
            }
            String friendlyLabel = maintenanceLabelMap.getOrDefault(image.getId(), extractFileName(image.getFilePath()));
            table.addCell(createBodyCell(friendlyLabel));
            table.addCell(createBodyCell(job.getStatus() != null ? job.getStatus().name() : NOT_AVAILABLE));
            table.addCell(createAnomalyCell(job.getResultJson()));
            table.addCell(createAnnotationDetailsCell(job, friendlyLabel));
        }
        table.setSpacingAfter(12f);
        document.add(table);
    }

    private void addAnnotationHistoryTable(Document document, List<Image> maintenanceImages, Map<Long, String> maintenanceLabelMap)
            throws DocumentException {
        document.add(makeSectionHeader("Annotation Details"));

        if (maintenanceImages.isEmpty()) {
            document.add(new Paragraph("No maintenance images available for annotations.", BODY_FONT));
            document.add(new Paragraph(" "));
            return;
        }

        PdfPTable table = new PdfPTable(new float[] { 1f, 0.8f, 0.8f, 1.2f, 1.6f, 1f });
        table.setWidthPercentage(100);
        addHeaderCell(table, "Image");
        addHeaderCell(table, "Annotated By");
        addHeaderCell(table, "Type");
        addHeaderCell(table, "Comments");
        addHeaderCell(table, "Boxes");
        addHeaderCell(table, "Updated");

        boolean anyRow = false;
        for (Image image : maintenanceImages) {
            if (image == null || image.getId() == null) {
                continue;
            }
            AnalysisJob job = image.getAnalysisJob();
            Annotation annotation = job != null
                    ? annotationService.getAnnotationByAnalysisJobId(job.getId()).orElse(null)
                    : null;

            String imageLabel = maintenanceLabelMap.getOrDefault(image.getId(), "maintenance");
            table.addCell(createBodyCell(imageLabel));
            if (annotation == null) {
                table.addCell(createBodyCell("N/A"));
                table.addCell(createBodyCell("None"));
                table.addCell(createBodyCell("No comments"));
                table.addCell(createBodyCell("0"));
                table.addCell(createBodyCell("N/A"));
            } else {
                table.addCell(createBodyCell(annotation.getAnnotatorDisplayName()));
                table.addCell(createBodyCell(annotation.getAnnotationType().name()));
                table.addCell(createBodyCell(
                        annotation.getComments() != null && !annotation.getComments().isBlank()
                                ? annotation.getComments()
                                : "No comments"));

                List<AnnotationBox> boxes = annotation.getAnnotationBoxes();
                table.addCell(createBoxListCell(boxes));
                table.addCell(createBodyCell(
                        formatDate(annotation.getUpdatedAt() != null ? annotation.getUpdatedAt() : annotation.getCreatedAt())));
            }
            anyRow = true;
        }

        if (!anyRow) {
            document.add(new Paragraph("No annotations available.", BODY_FONT));
            document.add(new Paragraph(" "));
            return;
        }

        table.setSpacingAfter(12f);
        document.add(table);
    }

    private void addImageSection(Document document, String title, List<Path> images, String labelPrefix) throws DocumentException {
        document.add(makeSectionHeader(title));
        if (images.isEmpty()) {
            document.add(new Paragraph("Not available", BODY_FONT));
            document.add(new Paragraph(" "));
            return;
        }

        PdfPTable imageTable = new PdfPTable(2);
        imageTable.setWidthPercentage(100);
        imageTable.setSpacingAfter(12f);

        for (int i = 0; i < images.size(); i++) {
            Path path = images.get(i);
            String caption = String.format("%s_%d", labelPrefix, i + 1);
            imageTable.addCell(createImageCell(path, caption));
        }

        if (images.size() % 2 != 0) {
            PdfPCell filler = new PdfPCell();
            filler.setBorder(Rectangle.NO_BORDER);
            imageTable.addCell(filler);
        }

        document.add(imageTable);
    }

    private List<Path> collectMaintenanceAnnotatedImages(List<Image> maintenanceImages) {
        Set<Path> collected = new LinkedHashSet<>();

        for (Image image : maintenanceImages) {
            AnalysisJob job = image.getAnalysisJob();
            if (job != null && job.getBoxedImagePath() != null) {
                resolveUploadsPath(job.getBoxedImagePath()).ifPresent(collected::add);
                continue;
            }

            deriveBoxedPathFromOriginal(image).ifPresent(collected::add);

            resolveUploadsPath(image.getFilePath()).ifPresent(collected::add);
        }
        return new ArrayList<>(collected);
    }

    private List<Path> collectBaselineImages(MaintenanceRecord record) {
        return Optional.ofNullable(record.getInspection())
                .map(Inspection::getTransformerRecord)
                .map(TransformerRecord::getImages)
                .orElse(List.of())
                .stream()
                .filter(img -> "Baseline".equalsIgnoreCase(img.getType()))
                .map(Image::getFilePath)
                .map(this::resolveUploadsPath)
                .flatMap(Optional::stream)
                .toList();
    }

    private void addKeyValueCell(PdfPTable table, String key, String value) {
        table.addCell(createHeaderCell(key));
        table.addCell(createBodyCell(value));
    }

    private PdfPCell createHeaderCell(String value) {
        PdfPCell cell = new PdfPCell(new Phrase(safeValue(value), CELL_HEADER_FONT));
        cell.setBackgroundColor(new Color(240, 245, 252));
        cell.setPadding(6f);
        cell.setBorderColor(LINE_COLOR);
        cell.setBorderWidthBottom(1f);
        return cell;
    }

    private void addHeaderCell(PdfPTable table, String value) {
        PdfPCell cell = new PdfPCell(new Phrase(value, CELL_HEADER_FONT));
        cell.setBackgroundColor(new Color(235, 235, 235));
        cell.setPadding(6f);
        cell.setHorizontalAlignment(Rectangle.ALIGN_LEFT);
        table.addCell(cell);
    }

    private PdfPCell createBodyCell(String value) {
        PdfPCell cell = new PdfPCell(new Phrase(safeValue(value), CELL_FONT));
        cell.setPadding(6f);
        return cell;
    }

    private PdfPCell createSummaryCell(String label, String value, Color backgroundColor) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(backgroundColor);
        cell.setPadding(12f);
        cell.setBorder(Rectangle.NO_BORDER);

        Paragraph labelParagraph = new Paragraph(label.toUpperCase(Locale.ENGLISH), SUMMARY_LABEL_FONT);
        labelParagraph.setSpacingAfter(4f);
        Paragraph valueParagraph = new Paragraph(safeValue(value), SUMMARY_VALUE_FONT);

        cell.addElement(labelParagraph);
        cell.addElement(valueParagraph);
        return cell;
    }

    private PdfPCell createImageCell(Path path, String captionText) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(IMAGE_FRAME_BORDER);
        cell.setBackgroundColor(IMAGE_FRAME_BG);
        cell.setPadding(8f);

        try {
            com.lowagie.text.Image pdfImage = com.lowagie.text.Image
                    .getInstance(path.toAbsolutePath().toString());
            pdfImage.scaleToFit(240, 200);
            pdfImage.setAlignment(com.lowagie.text.Image.ALIGN_CENTER);
            cell.addElement(pdfImage);
        } catch (IOException | BadElementException imageError) {
            cell.addElement(new Paragraph("Image unavailable (" + path.getFileName() + ")", BODY_FONT));
        }

        Paragraph caption = new Paragraph(captionText != null ? captionText : path.getFileName().toString(), CAPTION_FONT);
        caption.setAlignment(Paragraph.ALIGN_CENTER);
        caption.setSpacingBefore(4f);
        cell.addElement(caption);

        return cell;
    }

    private Paragraph makeSectionHeader(String text) {
        Font coloredSectionFont = new Font(SECTION_FONT);
        coloredSectionFont.setColor(BRAND_PRIMARY);
        Paragraph section = new Paragraph(text, coloredSectionFont);
        section.setSpacingBefore(12f);
        section.setSpacingAfter(6f);
        return section;
    }

    private void addParagraph(Document document, String label, String value) throws DocumentException {
        Paragraph paragraph = new Paragraph(label + ": " + safeValue(value), BODY_FONT);
        paragraph.setSpacingAfter(4f);
        document.add(paragraph);
    }

    private void addListParagraph(Document document, String label, String value) throws DocumentException {
        if (value == null || value.isBlank()) {
            return;
        }
        Paragraph title = new Paragraph(label + ":", BODY_FONT);
        title.setSpacingAfter(4f);
        document.add(title);

        com.lowagie.text.List bulletList = new com.lowagie.text.List(false, 12f);
        bulletList.setListSymbol("\u2022 ");
        splitToLines(value).forEach(line -> bulletList.add(new ListItem(line, BODY_FONT)));
        document.add(bulletList);
    }

    private PdfPCell createAnomalyCell(String resultJson) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(6f);
        AnomalySummary summary = extractAnomalySummary(resultJson);

        if (summary.label != null) {
            Paragraph labelParagraph = new Paragraph("Label: " + summary.label, CELL_FONT);
            labelParagraph.setSpacingAfter(4f);
            cell.addElement(labelParagraph);
        }

        if (!summary.anomalies.isEmpty()) {
            for (String text : summary.anomalies) {
                cell.addElement(new Paragraph("\u2022 " + text, CELL_FONT));
            }
        } else {
            cell.addElement(new Paragraph("No anomalies detected", CELL_FONT));
        }
        return cell;
    }

    private PdfPCell createAnnotationDetailsCell(AnalysisJob job, String imageLabel) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(6f);

        if (job == null) {
            cell.addElement(new Paragraph("No analysis job", CELL_FONT));
            return cell;
        }

        Annotation annotation = annotationService.getAnnotationByAnalysisJobId(job.getId()).orElse(null);
        if (annotation == null) {
            cell.addElement(new Paragraph("No annotations yet", CELL_FONT));
            return cell;
        }

        cell.addElement(new Paragraph("Image: " + safeValue(imageLabel), CELL_FONT));
        cell.addElement(new Paragraph("Type: " + annotation.getAnnotationType().name(), CELL_FONT));
        if (annotation.getComments() != null && !annotation.getComments().isBlank()) {
            Paragraph comments = new Paragraph("Comments: " + annotation.getComments(), CELL_FONT);
            comments.setSpacingAfter(4f);
            cell.addElement(comments);
        }

        List<AnnotationBox> boxes = annotation.getAnnotationBoxes();
        if (boxes != null && !boxes.isEmpty()) {
            int max = Math.min(8, boxes.size());
            for (int i = 0; i < max; i++) {
                AnnotationBox box = boxes.get(i);
                String action = box.getAction() != null ? box.getAction().name() : "UNCHANGED";
                String type = box.getType() != null ? box.getType() : "Annotation";
                String confidence = box.getConfidence() != null
                        ? String.format(Locale.ENGLISH, "%.2f", box.getConfidence())
                        : "N/A";
                cell.addElement(new Paragraph(
                        String.format(Locale.ENGLISH, "%d) %s (conf %s) [%s]", i + 1, type, confidence, action),
                        CELL_FONT));
            }
            if (boxes.size() > max) {
                cell.addElement(new Paragraph(
                        String.format(Locale.ENGLISH, "... plus %d more boxes", boxes.size() - max), CAPTION_FONT));
            }
        } else {
            cell.addElement(new Paragraph("No boxes recorded", CELL_FONT));
        }

        Paragraph updated = new Paragraph(
                "Updated: " + formatDate(annotation.getUpdatedAt() != null ? annotation.getUpdatedAt() : annotation.getCreatedAt()),
                CAPTION_FONT);
        updated.setSpacingBefore(4f);
        cell.addElement(updated);

        return cell;
    }

    private AnomalySummary extractAnomalySummary(String resultJson) {
        if (resultJson == null || resultJson.isBlank()) {
            return new AnomalySummary(null, List.of());
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(resultJson);
            String label = root.path("label").asText(null);
            List<String> anomalies = new ArrayList<>();

            JsonNode boxes = root.path("boxes");
            if (boxes.isArray()) {
                for (int i = 0; i < boxes.size(); i++) {
                    JsonNode box = boxes.get(i);
                    String type = box.path("type").asText("Anomaly");
                    double confidence = box.path("confidence").asDouble(0);
                    anomalies.add(String.format(Locale.ENGLISH, "%s (%.1f%%)", type, confidence * 100));
                    if (anomalies.size() >= 6) {
                        break;
                    }
                }
            }

            return new AnomalySummary(label, anomalies);
        } catch (IOException parseError) {
            return new AnomalySummary(null, List.of("Result unavailable"));
        }
    }

    private String safeValue(String value) {
        return value == null || value.isBlank() ? NOT_AVAILABLE : value;
    }

    private List<String> splitToLines(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String[] parts = text.split("\\r?\\n|;");
        List<String> cleaned = new ArrayList<>();
        for (String p : parts) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) {
                cleaned.add(trimmed);
            }
        }
        if (cleaned.isEmpty() && text.contains(",")) {
            for (String p : text.split(",")) {
                String trimmed = p.trim();
                if (!trimmed.isEmpty()) {
                    cleaned.add(trimmed);
                }
            }
        }
        return cleaned;
    }

    private String formatNumber(Double value) {
        return value == null ? NOT_AVAILABLE : String.format(Locale.ENGLISH, "%.2f", value);
    }

    private String formatNumber(Double value, String suffix) {
        return value == null ? NOT_AVAILABLE : String.format(Locale.ENGLISH, "%.2f%s", value, suffix);
    }

    private String formatDate(java.time.LocalDateTime value) {
        return value == null ? NOT_AVAILABLE : dateTimeFormatter.format(value);
    }

    private String formatCoordinates(TransformerRecord transformerRecord) {
        if (transformerRecord.getLocationLat() == null || transformerRecord.getLocationLng() == null) {
            return NOT_AVAILABLE;
        }
        return String.format(Locale.ENGLISH, "%.5f, %.5f",
                transformerRecord.getLocationLat(),
                transformerRecord.getLocationLng());
    }

    private Optional<Path> deriveBoxedPathFromOriginal(Image image) {
        if (image.getFilePath() == null || image.getFilePath().isBlank()) {
            return Optional.empty();
        }

        String normalized = image.getFilePath().replace("\\", "/");
        int lastSlash = normalized.lastIndexOf('/');
        String fileName = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0) {
            return Optional.empty();
        }

        String baseName = fileName.substring(0, dotIndex);
        if (baseName.endsWith("_boxed")) {
            baseName = baseName.substring(0, baseName.length() - 6);
        }
        String extension = fileName.substring(dotIndex);
        String candidate = "analysis/" + baseName + "_boxed" + extension;
        return resolveUploadsPath(candidate);
    }

    private Optional<Path> resolveUploadsPath(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return Optional.empty();
        }

        String normalized = storedPath.replace("\\", "/");
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith("uploads/")) {
            normalized = normalized.substring("uploads/".length());
        }

        Path baseDir = Paths.get(uploadDirectory).toAbsolutePath().normalize();
        Path resolved = baseDir.resolve(normalized).normalize();
        if (!resolved.startsWith(baseDir)) {
            return Optional.empty();
        }
        if (!Files.exists(resolved)) {
            return Optional.empty();
        }
        return Optional.of(resolved);
    }

    private String extractFileName(String path) {
        if (path == null || path.isBlank()) {
            return NOT_AVAILABLE;
        }
        String normalized = path.replace("\\", "/");
        int index = normalized.lastIndexOf('/');
        return index >= 0 ? normalized.substring(index + 1) : normalized;
    }

    private static class AnomalySummary {
        private final String label;
        private final List<String> anomalies;

        private AnomalySummary(String label, List<String> anomalies) {
            this.label = label;
            this.anomalies = anomalies;
        }
    }

    private String summarizeChangedBoxes(List<AnnotationBox> boxes) {
        if (boxes == null || boxes.isEmpty()) {
            return "No boxes";
        }
        List<AnnotationBox> changed = boxes.stream()
                .filter(b -> b.getAction() != null && b.getAction() != AnnotationBox.BoxAction.UNCHANGED)
                .toList();
        if (changed.isEmpty()) {
            return "No user changes";
        }
        return String.format(Locale.ENGLISH, "%d change(s)", changed.size());
    }

    private PdfPCell createBoxListCell(List<AnnotationBox> boxes) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(6f);
        if (boxes == null || boxes.isEmpty()) {
            cell.addElement(new Paragraph("No boxes", CELL_FONT));
            return cell;
        }
        List<AnnotationBox> changed = boxes.stream()
                .filter(b -> b.getAction() != null && b.getAction() != AnnotationBox.BoxAction.UNCHANGED)
                .toList();
        if (changed.isEmpty()) {
            cell.addElement(new Paragraph("No user changes", CELL_FONT));
            return cell;
        }
        int preview = Math.min(5, changed.size());
        for (int i = 0; i < preview; i++) {
            AnnotationBox box = changed.get(i);
            String type = box.getType() != null ? box.getType() : "Box";
            String action = box.getAction() != null ? box.getAction().name() : "UNCHANGED";
            String confidence = box.getConfidence() != null
                    ? String.format(Locale.ENGLISH, "%.2f", box.getConfidence())
                    : "N/A";
            Paragraph line = new Paragraph(
                    String.format(Locale.ENGLISH, "%d) %s [%s] conf %s", i + 1, type, action, confidence),
                    CELL_FONT);
            line.setSpacingAfter(2f);
            cell.addElement(line);
        }
        if (changed.size() > preview) {
            cell.addElement(new Paragraph(
                    String.format(Locale.ENGLISH, "... plus %d more", changed.size() - preview),
                    CAPTION_FONT));
        }
        return cell;
    }
}
