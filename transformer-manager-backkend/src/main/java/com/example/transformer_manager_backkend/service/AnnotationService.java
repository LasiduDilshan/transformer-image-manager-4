package com.example.transformer_manager_backkend.service;

import com.example.transformer_manager_backkend.entity.*;
import com.example.transformer_manager_backkend.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class AnnotationService {

    private static final Logger logger = LoggerFactory.getLogger(AnnotationService.class);

    private final AnnotationRepository annotationRepository;
    private final AnnotationBoxRepository annotationBoxRepository;
    private final AnalysisJobRepository analysisJobRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.annotation.python.executable:python}")
    private String pythonExecutable;

    @Value("${app.annotation.refresh.script.path:./automatic-anamoly-detection/Model_Inference/refresh_boxes.py}")
    private String refreshScriptPath;

    public AnnotationService(AnnotationRepository annotationRepository,
            AnnotationBoxRepository annotationBoxRepository,
            AnalysisJobRepository analysisJobRepository) {
        this.annotationRepository = annotationRepository;
        this.annotationBoxRepository = annotationBoxRepository;
        this.analysisJobRepository = analysisJobRepository;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Get or create annotation for an analysis job
     */
    @Transactional
    public Annotation getOrCreateAnnotation(Long analysisJobId, Object annotator) {
        Optional<AnalysisJob> analysisJobOpt = analysisJobRepository.findById(analysisJobId);
        if (analysisJobOpt.isEmpty()) {
            throw new RuntimeException("Analysis job not found: " + analysisJobId);
        }

        AnalysisJob analysisJob = analysisJobOpt.get();

        if (analysisJob.getStatus() != AnalysisJob.AnalysisStatus.COMPLETED) {
            throw new RuntimeException("Cannot annotate incomplete analysis job");
        }

        Optional<Annotation> existingAnnotation = annotationRepository.findByAnalysisJob(analysisJob);
        if (existingAnnotation.isPresent()) {
            return existingAnnotation.get();
        }

        // Create new annotation
        Annotation annotation = new Annotation(analysisJob, analysisJob.getResultJson(), analysisJob.getResultJson());

        // Set annotator
        if (annotator instanceof User) {
            annotation.setAnnotatedByUser((User) annotator);
        } else if (annotator instanceof Admin) {
            annotation.setAnnotatedByAdmin((Admin) annotator);
        }

        // Parse original JSON and create annotation boxes
        List<AnnotationBox> boxes = parseJsonToAnnotationBoxes(analysisJob.getResultJson());
        annotation = annotationRepository.save(annotation);

        for (AnnotationBox box : boxes) {
            box.setAnnotation(annotation);
            box.setAction(AnnotationBox.BoxAction.UNCHANGED);
        }
        annotationBoxRepository.saveAll(boxes);
        annotation.setAnnotationBoxes(boxes);

        logger.info("Created annotation for analysis job {}", analysisJobId);
        return annotation;
    }

    /**
     * Get annotation by its ID (ensures boxes are initialized for serialization)
     */
    @Transactional(readOnly = true)
    public Optional<Annotation> getAnnotationById(Long annotationId) {
        Optional<Annotation> opt = annotationRepository.findById(annotationId);
        opt.ifPresent(a -> {
            // touch boxes to initialize lazy collection
            if (a.getAnnotationBoxes() != null) {
                a.getAnnotationBoxes().size();
            }
        });
        return opt;
    }

    /**
     * Update annotation with new box data
     */
    @Transactional
    public Annotation updateAnnotation(Long annotationId, List<AnnotationBoxDTO> boxDTOs,
            String comments, Object annotator) {
        Optional<Annotation> annotationOpt = annotationRepository.findById(annotationId);
        if (annotationOpt.isEmpty()) {
            throw new RuntimeException("Annotation not found: " + annotationId);
        }

        Annotation annotation = annotationOpt.get();

        // Allow any user to edit for now (open access per request)

        // Update comments
        annotation.setComments(comments != null ? comments : "");
        annotation.setAnnotationType(Annotation.AnnotationType.EDITED);

        // Clear existing boxes by modifying the managed collection
        List<AnnotationBox> managedBoxes = annotation.getAnnotationBoxes();
        if (managedBoxes != null && !managedBoxes.isEmpty()) {
            for (AnnotationBox b : new ArrayList<>(managedBoxes)) {
                b.setAnnotation(null);
            }
            managedBoxes.clear();
        }

        // Create new boxes from DTOs and add to managed collection
        if (boxDTOs == null) {
            boxDTOs = new ArrayList<>();
        }
        for (AnnotationBoxDTO dto : boxDTOs) {
            int x = dto.getX() != null ? dto.getX() : 0;
            int y = dto.getY() != null ? dto.getY() : 0;
            int w = dto.getWidth() != null ? Math.max(1, dto.getWidth()) : 1;
            int h = dto.getHeight() != null ? Math.max(1, dto.getHeight()) : 1;
            String type = (dto.getType() != null && !dto.getType().isBlank()) ? dto.getType() : "Custom Anomaly";

            AnnotationBox box = new AnnotationBox(x, y, w, h, type, dto.getConfidence());
            box.setAnnotation(annotation);
            box.setAction(dto.getAction() != null ? dto.getAction() : AnnotationBox.BoxAction.UNCHANGED);
            box.setComments(dto.getComments());
            managedBoxes.add(box);
        }

        // Update modified JSON based on current managed boxes
        String modifiedJson = createModifiedJson(annotation, managedBoxes);
        String persistedJson = modifiedJson;

        // Update the JSON file, refresh image, and capture the final JSON that was written
        try {
            persistedJson = updateJsonFileAndRefreshImage(annotation, modifiedJson, managedBoxes);
        } catch (Exception e) {
            logger.error("Failed to update JSON file and refresh image for annotation {}", annotationId, e);
            persistedJson = modifiedJson;
        }

        annotation.setModifiedResultJson(persistedJson);
        annotation = annotationRepository.save(annotation);

        AnalysisJob job = annotation.getAnalysisJob();
        if (job != null) {
            job.setResultJson(persistedJson);
            analysisJobRepository.save(job);
        }

        logger.info("Updated annotation {} with {} boxes", annotationId, managedBoxes.size());
        return annotation;
    }

    /**
     * Build merged annotation report (AI detections + user edits + metadata)
     */
    @Transactional(readOnly = true)
    public Optional<String> generateAnnotationReport(Long analysisJobId) {
        try {
            Optional<Annotation> annotationOpt = annotationRepository.findByAnalysisJobId(analysisJobId);
            if (annotationOpt.isEmpty()) {
                return generateAutoReportFromJob(analysisJobId);
            }

            Annotation annotation = annotationOpt.get();
            ObjectNode root = objectMapper.createObjectNode();
            root.put("reportGeneratedAt", LocalDateTime.now().toString());
            root.put("analysisJobId", analysisJobId);
            root.put("annotationId", annotation.getId());
            root.put("annotationType", annotation.getAnnotationType().name());
            if (annotation.getComments() != null) {
                root.put("comments", annotation.getComments());
            }
            if (annotation.getCreatedAt() != null) {
                root.put("createdAt", annotation.getCreatedAt().toString());
            }
            if (annotation.getUpdatedAt() != null) {
                root.put("updatedAt", annotation.getUpdatedAt().toString());
            }

            AnalysisJob job = annotation.getAnalysisJob();
            if (job != null) {
                ObjectNode jobNode = root.putObject("analysisJob");
                jobNode.put("status", job.getStatus().name());
                if (job.getCompletedAt() != null) {
                    jobNode.put("completedAt", job.getCompletedAt().toString());
                }
                if (job.getStartedAt() != null) {
                    jobNode.put("startedAt", job.getStartedAt().toString());
                }
                if (job.getResultJson() != null) {
                    jobNode.put("resultJsonLength", job.getResultJson().length());
                }
                if (job.getBoxedImagePath() != null) {
                    jobNode.put("boxedImagePath", job.getBoxedImagePath());
                }
                if (job.getImage() != null) {
                    Image image = job.getImage();
                    ObjectNode imageNode = jobNode.putObject("image");
                    imageNode.put("id", image.getId());
                    if (image.getFilePath() != null) {
                        imageNode.put("filePath", image.getFilePath());
                    }
                    if (image.getType() != null) {
                        imageNode.put("type", image.getType());
                    }
                    if (image.getInspection() != null) {
                        imageNode.put("inspectionId", image.getInspection().getId());
                    }
                    if (image.getTransformerRecord() != null) {
                        imageNode.put("transformerId", image.getTransformerRecord().getId());
                    }
                }
            }

            String annotatorType = "UNKNOWN";
            if (annotation.getAnnotatedByUser() != null) {
                annotatorType = "USER";
            } else if (annotation.getAnnotatedByAdmin() != null) {
                annotatorType = "ADMIN";
            }
            ObjectNode annotatorNode = root.putObject("annotator");
            annotatorNode.put("type", annotatorType);
            annotatorNode.put("displayName", annotation.getAnnotatorDisplayName());

            JsonNode originalDetections = readJsonSafely(annotation.getOriginalResultJson());
            JsonNode finalAnnotations = readJsonSafely(annotation.getModifiedResultJson());
            root.set("originalAIDetections", originalDetections);
            root.set("finalUserAnnotations", finalAnnotations);

            ArrayNode boxesArray = objectMapper.createArrayNode();
            int addedCount = 0;
            int modifiedCount = 0;
            int deletedCount = 0;
            int unchangedCount = 0;

            if (annotation.getAnnotationBoxes() != null) {
                for (AnnotationBox box : annotation.getAnnotationBoxes()) {
                    ObjectNode boxNode = objectMapper.createObjectNode();
                    boxNode.put("x", box.getX());
                    boxNode.put("y", box.getY());
                    boxNode.put("width", box.getWidth());
                    boxNode.put("height", box.getHeight());
                    if (box.getType() != null) {
                        boxNode.put("type", box.getType());
                    }
                    if (box.getConfidence() != null) {
                        boxNode.put("confidence", box.getConfidence());
                    }
                    if (box.getComments() != null) {
                        boxNode.put("comments", box.getComments());
                    }
                    AnnotationBox.BoxAction action = box.getAction();
                    if (action != null) {
                        boxNode.put("action", action.name());
                        switch (action) {
                            case ADDED:
                                addedCount++;
                                break;
                            case MODIFIED:
                                modifiedCount++;
                                break;
                            case DELETED:
                                deletedCount++;
                                break;
                            default:
                                unchangedCount++;
                                break;
                        }
                    } else {
                        unchangedCount++;
                    }
                    boxesArray.add(boxNode);
                }
            }
            root.set("annotationBoxes", boxesArray);

            ObjectNode summaryNode = root.putObject("summary");
            summaryNode.put("totalBoxes", boxesArray.size());
            summaryNode.put("addedBoxes", addedCount);
            summaryNode.put("modifiedBoxes", modifiedCount);
            summaryNode.put("deletedBoxes", deletedCount);
            summaryNode.put("unchangedBoxes", unchangedCount);
            summaryNode.put("annotatorType", annotatorType);
            summaryNode.put("annotatorName", annotation.getAnnotatorDisplayName());
            summaryNode.put("commentsProvided",
                    annotation.getComments() != null && !annotation.getComments().isBlank());

            return Optional.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        } catch (Exception e) {
            logger.error("Failed to generate annotation report for analysis job {}", analysisJobId, e);
            return Optional.empty();
        }
    }

    /**
     * Build a minimal report directly from the analysis job when no annotation exists yet.
     */
    private Optional<String> generateAutoReportFromJob(Long analysisJobId) {
        Optional<AnalysisJob> jobOpt = analysisJobRepository.findById(analysisJobId);
        if (jobOpt.isEmpty()) {
            return Optional.empty();
        }
        AnalysisJob job = jobOpt.get();
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("reportGeneratedAt", LocalDateTime.now().toString());
            root.put("analysisJobId", analysisJobId);
            root.put("annotationId", (Long) null);
            root.put("annotationType", "AUTO_GENERATED");
            root.put("comments", "");

            ObjectNode jobNode = root.putObject("analysisJob");
            jobNode.put("status", job.getStatus() != null ? job.getStatus().name() : "UNKNOWN");
            if (job.getCompletedAt() != null) {
                jobNode.put("completedAt", job.getCompletedAt().toString());
            }
            if (job.getStartedAt() != null) {
                jobNode.put("startedAt", job.getStartedAt().toString());
            }
            if (job.getResultJson() != null) {
                jobNode.put("resultJsonLength", job.getResultJson().length());
            }
            if (job.getBoxedImagePath() != null) {
                jobNode.put("boxedImagePath", job.getBoxedImagePath());
            }
            if (job.getImage() != null) {
                Image image = job.getImage();
                ObjectNode imageNode = jobNode.putObject("image");
                imageNode.put("id", image.getId());
                if (image.getFilePath() != null) {
                    imageNode.put("filePath", image.getFilePath());
                }
                if (image.getType() != null) {
                    imageNode.put("type", image.getType());
                }
                if (image.getInspection() != null) {
                    imageNode.put("inspectionId", image.getInspection().getId());
                }
                if (image.getTransformerRecord() != null) {
                    imageNode.put("transformerId", image.getTransformerRecord().getId());
                }
            }

            JsonNode detections = readJsonSafely(job.getResultJson());
            root.set("originalAIDetections", detections);
            root.set("finalUserAnnotations", detections);

            int boxesCount = countBoxes(detections);

            ArrayNode boxesArray = objectMapper.createArrayNode();
            if (detections != null && detections.has("boxes") && detections.get("boxes").isArray()) {
                for (JsonNode box : detections.get("boxes")) {
                    boxesArray.add(box);
                }
            }
            root.set("annotationBoxes", boxesArray);

            ObjectNode summaryNode = root.putObject("summary");
            summaryNode.put("totalBoxes", boxesCount);
            summaryNode.put("addedBoxes", 0);
            summaryNode.put("modifiedBoxes", 0);
            summaryNode.put("deletedBoxes", 0);
            summaryNode.put("unchangedBoxes", boxesCount);
            summaryNode.put("annotatorType", "SYSTEM");
            summaryNode.put("annotatorName", "System (auto)");
            summaryNode.put("commentsProvided", false);

            return Optional.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        } catch (Exception e) {
            logger.error("Failed to generate auto annotation report for analysis job {}", analysisJobId, e);
            return Optional.empty();
        }
    }

    /**
     * Get annotation by analysis job ID
     */
    public Optional<Annotation> getAnnotationByAnalysisJobId(Long analysisJobId) {
        return annotationRepository.findByAnalysisJobId(analysisJobId);
    }

    /**
     * Get all annotations for an inspection
     */
    public List<Annotation> getAnnotationsByInspectionId(Long inspectionId) {
        return annotationRepository.findByInspectionId(inspectionId);
    }

    /**
     * Export feedback log as JSON
     */
    public String exportFeedbackLogAsJson() {
        List<Annotation> annotations = annotationRepository.findAllWithFeedbackData();
        ObjectNode rootNode = objectMapper.createObjectNode();
        ArrayNode feedbackArray = objectMapper.createArrayNode();

        for (Annotation annotation : annotations) {
            ObjectNode feedbackEntry = objectMapper.createObjectNode();
            AnalysisJob job = annotation.getAnalysisJob();
            Image image = job.getImage();

            feedbackEntry.put("imageId", image.getId());
            feedbackEntry.put("imagePath", image.getFilePath());
            feedbackEntry.put("analysisJobId", job.getId());
            feedbackEntry.put("transformerId",
                    image.getTransformerRecord() != null ? image.getTransformerRecord().getId() : null);
            feedbackEntry.put("inspectionId", image.getInspection() != null ? image.getInspection().getId() : null);

            try {
                feedbackEntry.set("originalAIDetections", objectMapper.readTree(annotation.getOriginalResultJson()));
                feedbackEntry.set("finalUserAnnotations", objectMapper.readTree(annotation.getModifiedResultJson()));
            } catch (Exception e) {
                logger.error("Error parsing JSON for annotation {}", annotation.getId(), e);
            }

            feedbackEntry.put("annotatorType", annotation.getAnnotatedByUser() != null ? "USER" : "ADMIN");
            feedbackEntry.put("annotatorName", annotation.getAnnotatorDisplayName());
            feedbackEntry.put("annotationType", annotation.getAnnotationType().toString());
            feedbackEntry.put("comments", annotation.getComments());
            feedbackEntry.put("createdAt", annotation.getCreatedAt().toString());
            feedbackEntry.put("updatedAt", annotation.getUpdatedAt().toString());

            feedbackArray.add(feedbackEntry);
        }

        rootNode.set("feedbackLog", feedbackArray);
        rootNode.put("exportTimestamp", java.time.LocalDateTime.now().toString());
        rootNode.put("totalEntries", feedbackArray.size());

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);
        } catch (Exception e) {
            logger.error("Error serializing feedback log", e);
            return "{}";
        }
    }

    /**
     * Parse JSON result to annotation boxes
     */
    private List<AnnotationBox> parseJsonToAnnotationBoxes(String resultJson) {
        List<AnnotationBox> boxes = new ArrayList<>();
        try {
            JsonNode rootNode = objectMapper.readTree(resultJson);
            JsonNode boxesNode = rootNode.get("boxes");

            if (boxesNode != null && boxesNode.isArray()) {
                for (JsonNode boxNode : boxesNode) {
                    JsonNode boxArray = boxNode.get("box");
                    if (boxArray != null && boxArray.isArray() && boxArray.size() >= 4) {
                        int x = boxArray.get(0).asInt();
                        int y = boxArray.get(1).asInt();
                        int width = boxArray.get(2).asInt();
                        int height = boxArray.get(3).asInt();
                        String type = boxNode.get("type").asText("Unknown");
                        Double confidence = boxNode.has("confidence") ? boxNode.get("confidence").asDouble() : null;

                        AnnotationBox box = new AnnotationBox(x, y, width, height, type, confidence);
                        boxes.add(box);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing JSON to annotation boxes", e);
        }
        return boxes;
    }

    private int countBoxes(JsonNode detections) {
        if (detections == null) {
            return 0;
        }
        JsonNode boxesNode = detections.get("boxes");
        return boxesNode != null && boxesNode.isArray() ? boxesNode.size() : 0;
    }

    /**
     * Create modified JSON from annotation and boxes
     */
    private String createModifiedJson(Annotation annotation, List<AnnotationBox> boxes) {
        try {
            JsonNode originalNode = objectMapper.readTree(annotation.getOriginalResultJson());
            ObjectNode modifiedNode = originalNode.deepCopy();

            // Update the boxes array
            ArrayNode boxesArray = objectMapper.createArrayNode();
            for (AnnotationBox box : boxes) {
                ObjectNode boxNode = objectMapper.createObjectNode();
                ArrayNode coordsArray = objectMapper.createArrayNode();
                coordsArray.add(box.getX());
                coordsArray.add(box.getY());
                coordsArray.add(box.getWidth());
                coordsArray.add(box.getHeight());

                boxNode.set("box", coordsArray);
                boxNode.put("type", box.getType());
                if (box.getConfidence() != null) {
                    boxNode.put("confidence", box.getConfidence());
                }
                boxesArray.add(boxNode);
            }

            modifiedNode.set("boxes", boxesArray);

            return objectMapper.writeValueAsString(modifiedNode);
        } catch (Exception e) {
            logger.error("Error creating modified JSON", e);
            return annotation.getOriginalResultJson();
        }
    }

    /**
     * Update JSON file and refresh the boxed image using Python script
     */
    private String updateJsonFileAndRefreshImage(Annotation annotation, String modifiedJson, List<AnnotationBox> boxes)
            throws IOException, InterruptedException {
        if (annotation == null) {
            logger.warn("Skipping image refresh because annotation is null");
            return modifiedJson;
        }

        AnalysisJob job = annotation.getAnalysisJob();
        if (job == null) {
            logger.warn("Annotation {} has no associated analysis job; skipping image refresh", annotation.getId());
            return modifiedJson;
        }

        String boxedImageWebPath = normalizeWebPath(firstNonBlank(
                job.getBoxedImagePath(),
                job.getImage() != null ? job.getImage().getFilePath() : null));

        if (boxedImageWebPath == null) {
            logger.warn("No boxed image path available for annotation {}", annotation.getId());
            return modifiedJson;
        }

        String fileName = boxedImageWebPath.substring(boxedImageWebPath.lastIndexOf('/') + 1);
        String extension = extractExtension(fileName);
        String baseNameWithSuffix = removeExtension(fileName);
        String baseName = stripBoxedSuffix(baseNameWithSuffix);

        Path analysisDir = Paths.get("uploads", "analysis");
        Files.createDirectories(analysisDir);

        Path jsonFilePath = analysisDir.resolve(baseName + ".json");
        String boxedFileName = baseName + "_boxed" + extension;
        Path boxedImagePath = analysisDir.resolve(boxedFileName);
        Path originalImagePath = resolveOriginalImagePath(baseName, extension, annotation);

        String adjustedJson = adjustJsonPaths(annotation, modifiedJson, originalImagePath, boxedImagePath);
        Files.writeString(jsonFilePath, adjustedJson, StandardCharsets.UTF_8);
        logger.info("Updated JSON file: {}", jsonFilePath);

        List<AnnotationBox> safeBoxes = boxes != null ? boxes : Collections.emptyList();
        boolean refreshed = refreshBoxedImageWithJava(annotation, safeBoxes, boxedImagePath, originalImagePath, extension);
        if (!refreshed) {
            logger.info("Falling back to Python refresh script for annotation {}", annotation.getId());
            runRefreshScript(jsonFilePath);
        }

        String webBoxedPath = "/analysis/" + boxedFileName;
        if (job != null) {
            job.setBoxedImagePath(webBoxedPath);
            if (job.getImage() != null) {
                job.getImage().setFilePath(webBoxedPath);
            }
        }

        return adjustedJson;
    }

    private JsonNode readJsonSafely(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            logger.warn("Failed to parse JSON content safely", e);
            return objectMapper.createObjectNode();
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String normalizeWebPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String normalized = path.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith("uploads/")) {
            normalized = normalized.substring("uploads/".length());
        }
        return normalized;
    }

    private String extractExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return ".jpg";
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot);
        }
        return ".jpg";
    }

    private String removeExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        int lastDot = fileName.lastIndexOf('.');
        return lastDot >= 0 ? fileName.substring(0, lastDot) : fileName;
    }

    private String stripBoxedSuffix(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return name.endsWith("_boxed") ? name.substring(0, name.length() - "_boxed".length()) : name;
    }

    private Path resolveOriginalImagePath(String baseName, String extension, Annotation annotation) {
        String ext = (extension != null && !extension.isBlank()) ? extension : ".jpg";
        if (!ext.startsWith(".")) {
            ext = "." + ext;
        }

        Path expectedUpload = Paths.get("uploads", baseName + ext);
        if (Files.exists(expectedUpload)) {
            return expectedUpload;
        }

        Path fromJson = resolvePathFromJson(annotation);
        if (fromJson != null && Files.exists(fromJson)) {
            return fromJson;
        }

        Path boxed = Paths.get("uploads", "analysis", baseName + "_boxed" + ext);
        if (Files.exists(boxed)) {
            return boxed;
        }

        return expectedUpload;
    }

    private Path resolvePathFromJson(Annotation annotation) {
        if (annotation == null || annotation.getOriginalResultJson() == null) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(annotation.getOriginalResultJson());
            String imagePath = node.path("image").asText(null);
            if (imagePath != null && !imagePath.isBlank()) {
                return toLocalPath(imagePath);
            }
        } catch (Exception e) {
            logger.warn("Unable to resolve original image path from annotation {}", annotation.getId(), e);
        }
        return null;
    }

    private Path toLocalPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        String normalized = rawPath.trim().replace('\\', '/');

        if (normalized.startsWith("/mnt/") && normalized.length() > 6) {
            char drive = normalized.charAt(5);
            String remainder = normalized.substring(6).replace('/', '\\');
            return Paths.get(String.valueOf(drive).toUpperCase(Locale.ROOT) + ":\\" + remainder);
        }

        if (normalized.startsWith("/uploads/")) {
            String relative = normalized.substring("/uploads/".length());
            return Paths.get("uploads", relative);
        }

        if (normalized.startsWith("/analysis/")) {
            String relative = normalized.substring(1);
            return Paths.get("uploads", relative);
        }

        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return Paths.get(normalized);
    }

    private String adjustJsonPaths(Annotation annotation, String json, Path originalImagePath, Path boxedImagePath) {
        if (json == null || json.isBlank()) {
            return json;
        }
        try {
            ObjectNode node = (ObjectNode) objectMapper.readTree(json);
            if (originalImagePath != null) {
                node.put("image", originalImagePath.toAbsolutePath().toString());
            }
            if (boxedImagePath != null) {
                node.put("boxed_image", boxedImagePath.toAbsolutePath().toString());
            }
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            logger.warn("Failed to adjust JSON paths for annotation {}", annotation != null ? annotation.getId() : "unknown", e);
            return json;
        }
    }

    private boolean refreshBoxedImageWithJava(Annotation annotation, List<AnnotationBox> boxes, Path boxedImagePath,
            Path originalImagePath, String extensionWithDot) {
        try {
            Path sourcePath = originalImagePath;
            if (sourcePath == null || !Files.exists(sourcePath)) {
                logger.warn("Source image not found for annotation {} at {}", annotation.getId(), sourcePath);
                return false;
            }

            BufferedImage sourceImage = ImageIO.read(sourcePath.toFile());
            if (sourceImage == null) {
                logger.warn("Failed to read source image for annotation {}", annotation.getId());
                return false;
            }

            BufferedImage outputImage = new BufferedImage(
                    sourceImage.getWidth(),
                    sourceImage.getHeight(),
                    BufferedImage.TYPE_INT_RGB);

            Graphics2D graphics = outputImage.createGraphics();
            graphics.drawImage(sourceImage, 0, 0, null);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            float strokeWidth = Math.max(2f, Math.min(sourceImage.getWidth(), sourceImage.getHeight()) * 0.004f);
            graphics.setStroke(new BasicStroke(strokeWidth));
            graphics.setFont(new Font("SansSerif", Font.BOLD,
                    Math.max(12, (int) (Math.min(sourceImage.getWidth(), sourceImage.getHeight()) * 0.03))));

            for (AnnotationBox box : boxes) {
                if (box == null) {
                    continue;
                }
                int x = Math.max(0, box.getX());
                int y = Math.max(0, box.getY());
                int width = Math.max(1, box.getWidth());
                int height = Math.max(1, box.getHeight());
                Color color = chooseColorForLabel(box.getType());

                graphics.setColor(color);
                graphics.drawRect(x, y, width, height);

                String label = buildLabel(box);
                if (!label.isBlank()) {
                    drawLabel(graphics, label, color, x, y, width, height, strokeWidth,
                            outputImage.getWidth(), outputImage.getHeight());
                }
            }

            graphics.dispose();

            Files.createDirectories(boxedImagePath.getParent());
            String formatName = (extensionWithDot != null && extensionWithDot.startsWith("."))
                    ? extensionWithDot.substring(1)
                    : extensionWithDot;
            if (formatName == null || formatName.isBlank()) {
                formatName = "jpg";
            }

            boolean written = ImageIO.write(outputImage, formatName, boxedImagePath.toFile());
            if (!written) {
                logger.warn("ImageIO could not write format {} for {}", formatName, boxedImagePath);
                return false;
            }

            logger.info("Refreshed boxed image for annotation {} at {}", annotation.getId(), boxedImagePath);
            return true;
        } catch (Exception e) {
            logger.error("Failed to refresh boxed image via Java for annotation {}", annotation != null ? annotation.getId() : "unknown", e);
            return false;
        }
    }

    private void drawLabel(Graphics2D graphics, String label, Color boxColor,
            int x, int y, int width, int height, float strokeWidth, int imageWidth, int imageHeight) {
        FontMetrics metrics = graphics.getFontMetrics();
        int padding = Math.max(4, Math.round(strokeWidth));
        int textWidth = metrics.stringWidth(label);
        int textHeight = metrics.getAscent() + metrics.getDescent();

        int rectX = clamp(x, padding, Math.max(0, imageWidth - textWidth - padding * 2));
        int rectY = y - textHeight - padding;
        if (rectY < padding) {
            rectY = clamp(y + height + padding, padding, imageHeight - textHeight - padding);
        }

        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
        graphics.setColor(Color.BLACK);
        graphics.fillRect(rectX - padding, rectY - padding / 2, textWidth + padding * 2, textHeight + padding);
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        graphics.setColor(Color.WHITE);
        graphics.drawString(label, rectX, rectY + textHeight - metrics.getDescent());
        graphics.setColor(boxColor);
    }

    private Color chooseColorForLabel(String type) {
        if (type == null) {
            return Color.RED;
        }
        String normalized = type.toLowerCase(Locale.ROOT);
        if (normalized.contains("potential") || normalized.contains("full wire overload")) {
            return new Color(255, 215, 0);
        }
        if (normalized.contains("custom")) {
            return new Color(0, 191, 255);
        }
        return Color.RED;
    }

    private String buildLabel(AnnotationBox box) {
        StringBuilder label = new StringBuilder();
        if (box.getType() != null && !box.getType().isBlank()) {
            label.append(box.getType());
        }
        if (box.getConfidence() != null) {
            if (label.length() > 0) {
                label.append(" ");
            }
            label.append("(").append(String.format(Locale.ROOT, "%.2f", box.getConfidence())).append(")");
        }
        return label.toString();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private void runRefreshScript(Path jsonFilePath) throws IOException, InterruptedException {
        String command = String.format("%s \"%s\" --json \"%s\"",
                pythonExecutable,
                refreshScriptPath,
                jsonFilePath.toAbsolutePath().toString());

        logger.info("Running refresh command: {}", command);

        ProcessBuilder processBuilder = new ProcessBuilder();
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows")) {
            processBuilder.command("cmd", "/c", command);
        } else {
            processBuilder.command("bash", "-c", command);
        }

        Process process = processBuilder.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

            String line;
            while ((line = reader.readLine()) != null) {
                logger.info("Refresh script output: {}", line);
            }
            while ((line = errorReader.readLine()) != null) {
                logger.warn("Refresh script error: {}", line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode == 0) {
            logger.info("Successfully refreshed boxed image via Python script");
        } else {
            logger.error("Refresh script failed with exit code: {}", exitCode);
        }
    }

    /**
     * DTO for annotation box data transfer
     */
    public static class AnnotationBoxDTO {
        private Integer x;
        private Integer y;
        private Integer width;
        private Integer height;
        private String type;
        private Double confidence;
        private AnnotationBox.BoxAction action;
        private String comments;

        // Constructors
        public AnnotationBoxDTO() {
        }

        public AnnotationBoxDTO(Integer x, Integer y, Integer width, Integer height, String type, Double confidence) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.type = type;
            this.confidence = confidence;
        }

        // Getters and Setters
        public Integer getX() {
            return x;
        }

        public void setX(Integer x) {
            this.x = x;
        }

        public Integer getY() {
            return y;
        }

        public void setY(Integer y) {
            this.y = y;
        }

        public Integer getWidth() {
            return width;
        }

        public void setWidth(Integer width) {
            this.width = width;
        }

        public Integer getHeight() {
            return height;
        }

        public void setHeight(Integer height) {
            this.height = height;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Double getConfidence() {
            return confidence;
        }

        public void setConfidence(Double confidence) {
            this.confidence = confidence;
        }

        public AnnotationBox.BoxAction getAction() {
            return action;
        }

        public void setAction(AnnotationBox.BoxAction action) {
            this.action = action;
        }

        public String getComments() {
            return comments;
        }

        public void setComments(String comments) {
            this.comments = comments;
        }
    }
}
