package com.example.transformer_manager_backkend.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.transformer_manager_backkend.entity.AnalysisJob;
import com.example.transformer_manager_backkend.entity.Image;
import com.example.transformer_manager_backkend.entity.Inspection;
import com.example.transformer_manager_backkend.entity.MaintenanceRecord;
import com.example.transformer_manager_backkend.entity.TransformerRecord;
import com.example.transformer_manager_backkend.repository.MaintenanceRecordRepository;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

@Service
public class MaintenanceRecordPdfService {

    private static final String NOT_AVAILABLE = "N/A";
    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
    private static final Font SECTION_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
    private static final Font BODY_FONT = FontFactory.getFont(FontFactory.HELVETICA, 11);
    private static final Font CELL_HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
    private static final Font CELL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 10);

    private final MaintenanceRecordRepository maintenanceRecordRepository;
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm", Locale.ENGLISH);

    @Value("${upload.directory}")
    private String uploadDirectory;

    public MaintenanceRecordPdfService(MaintenanceRecordRepository maintenanceRecordRepository) {
        this.maintenanceRecordRepository = maintenanceRecordRepository;
    }

    @Transactional(readOnly = true)
    public byte[] generatePdf(Long recordId) {
        MaintenanceRecord record = maintenanceRecordRepository.findById(recordId)
                .orElseThrow(() -> new RuntimeException("Maintenance record not found"));

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter.getInstance(document, baos);
            document.open();

            addHeader(document, record);
            addTransformerSection(document, record);
            addElectricalSection(document, record);
            addMaintenanceSection(document, record);
            addAnalysisSummary(document, record);
            addImageSection(document, "Maintenance Images", collectMaintenanceImages(record));
            addImageSection(document, "Annotated Images", collectAnnotatedImages(record));
            addImageSection(document, "Baseline Images", collectBaselineImages(record));

            document.close();
            return baos.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new RuntimeException("Failed to generate maintenance record PDF", e);
        }
    }

    private void addHeader(Document document, MaintenanceRecord record) throws DocumentException {
        Paragraph title = new Paragraph("Maintenance Record #" + record.getId(), TITLE_FONT);
        title.setSpacingAfter(8f);
        document.add(title);

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingAfter(10f);
        addKeyValueCell(table, "Created At", formatDate(record.getCreatedAt()));
        addKeyValueCell(table, "Status", safeValue(record.getRecordStatus()));
        addKeyValueCell(table, "Inspector", safeValue(record.getInspectorName()));
        addKeyValueCell(table, "Inspection Date", formatDate(Optional.ofNullable(record.getInspection()).map(Inspection::getInspectionDate).orElse(null)));
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
        addParagraph(document, "Detected Anomalies", record.getDetectedAnomalies());
        addParagraph(document, "Corrective Actions", record.getCorrectiveActions());
        addParagraph(document, "Recommended Action", record.getRecommendedAction());
        addParagraph(document, "Engineer Notes", record.getEngineerNotes());
        addParagraph(document, "Additional Remarks", record.getAdditionalRemarks());
    }

    private void addAnalysisSummary(Document document, MaintenanceRecord record) throws DocumentException {
        List<Image> maintenanceImages = Optional.ofNullable(record.getInspection())
                .map(Inspection::getImages)
                .orElse(List.of());

        boolean hasAnalysis = maintenanceImages.stream().anyMatch(img -> img.getAnalysisJob() != null);
        document.add(makeSectionHeader("Anomaly Analysis"));
        if (!hasAnalysis) {
            document.add(new Paragraph("No anomaly analysis results available.", BODY_FONT));
            document.add(new Paragraph(" ")); // spacer
            return;
        }

        PdfPTable table = new PdfPTable(new float[] { 1, 1, 1 });
        table.setWidthPercentage(100);
        addHeaderCell(table, "Image");
        addHeaderCell(table, "Status");
        addHeaderCell(table, "Analysis Result");

        for (Image image : maintenanceImages) {
            AnalysisJob job = image.getAnalysisJob();
            if (job == null) {
                continue;
            }
            table.addCell(createBodyCell(extractFileName(image.getFilePath())));
            table.addCell(createBodyCell(job.getStatus() != null ? job.getStatus().name() : NOT_AVAILABLE));
            table.addCell(createBodyCell(shorten(job.getResultJson())));
        }
        table.setSpacingAfter(12f);
        document.add(table);
    }

    private void addImageSection(Document document, String title, List<Path> images) throws DocumentException {
        document.add(makeSectionHeader(title));
        if (images.isEmpty()) {
            document.add(new Paragraph("Not available", BODY_FONT));
            document.add(new Paragraph(" ")); // spacer
            return;
        }

        for (Path path : images) {
            try {
                com.lowagie.text.Image pdfImage = com.lowagie.text.Image.getInstance(path.toAbsolutePath().toString());
                pdfImage.scaleToFit(450, 300);
                pdfImage.setAlignment(com.lowagie.text.Image.ALIGN_CENTER);
                document.add(pdfImage);
                Paragraph caption = new Paragraph(path.getFileName().toString(), BODY_FONT);
                caption.setAlignment(Paragraph.ALIGN_CENTER);
                caption.setSpacingAfter(8f);
                document.add(caption);
            } catch (Exception imageError) {
                document.add(new Paragraph("Failed to embed image: " + path.getFileName(), BODY_FONT));
            }
        }
    }

    private List<Path> collectMaintenanceImages(MaintenanceRecord record) {
        return Optional.ofNullable(record.getInspection())
                .map(Inspection::getImages)
                .orElse(List.of())
                .stream()
                .map(Image::getFilePath)
                .map(this::resolveUploadsPath)
                .flatMap(Optional::stream)
                .toList();
    }

    private List<Path> collectAnnotatedImages(MaintenanceRecord record) {
        List<Path> annotated = new ArrayList<>();
        List<Image> maintenanceImages = Optional.ofNullable(record.getInspection())
                .map(Inspection::getImages)
                .orElse(List.of());

        for (Image image : maintenanceImages) {
            AnalysisJob job = image.getAnalysisJob();
            if (job == null || job.getBoxedImagePath() == null) {
                continue;
            }
            resolveUploadsPath(job.getBoxedImagePath()).ifPresent(annotated::add);
        }
        return annotated;
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
        cell.setBackgroundColor(new Color(240, 240, 240));
        cell.setPadding(6f);
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

    private Paragraph makeSectionHeader(String text) {
        Paragraph section = new Paragraph(text, SECTION_FONT);
        section.setSpacingBefore(10f);
        section.setSpacingAfter(4f);
        return section;
    }

    private void addParagraph(Document document, String label, String value) throws DocumentException {
        Paragraph paragraph = new Paragraph(label + ": " + safeValue(value), BODY_FONT);
        paragraph.setSpacingAfter(4f);
        document.add(paragraph);
    }

    private String safeValue(String value) {
        return value == null || value.isBlank() ? NOT_AVAILABLE : value;
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

    private String shorten(String text) {
        if (text == null || text.isBlank()) {
            return NOT_AVAILABLE;
        }
        String trimmed = text.trim();
        return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 117) + "...";
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
}
