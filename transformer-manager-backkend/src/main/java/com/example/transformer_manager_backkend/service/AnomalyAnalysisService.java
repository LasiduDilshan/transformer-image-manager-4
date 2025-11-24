package com.example.transformer_manager_backkend.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.example.transformer_manager_backkend.entity.AnalysisJob;
import com.example.transformer_manager_backkend.entity.Image;
import com.example.transformer_manager_backkend.repository.AnalysisJobRepository;
import com.example.transformer_manager_backkend.repository.ImageRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AnomalyAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(AnomalyAnalysisService.class);

    private final AnalysisJobRepository analysisJobRepository;
    private final ImageRepository imageRepository;
    private final MLSettingsService mlSettingsService;
    private final ModelFeedbackService modelFeedbackService;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    private final RestTemplate restTemplate;
    private final String anomalyApiUrl;
    private final long anomalyApiTimeoutMs;
    private final Path analysisOutputDir;

    public AnomalyAnalysisService(AnalysisJobRepository analysisJobRepository, ImageRepository imageRepository,
            MLSettingsService mlSettingsService, ModelFeedbackService modelFeedbackService,
            RestTemplateBuilder restTemplateBuilder,
            @Value("${app.anomaly.remote.api.url:https://lasidu-automatic-anamoly-detection.hf.space/infer}") String anomalyApiUrl,
            @Value("${app.anomaly.remote.timeout-ms:120000}") long anomalyApiTimeoutMs) {
        this.analysisJobRepository = analysisJobRepository;
        this.imageRepository = imageRepository;
        this.mlSettingsService = mlSettingsService;
        this.modelFeedbackService = modelFeedbackService;
        this.objectMapper = new ObjectMapper();
        this.executorService = Executors.newSingleThreadExecutor();
        this.anomalyApiUrl = anomalyApiUrl;
        this.anomalyApiTimeoutMs = anomalyApiTimeoutMs;
        this.analysisOutputDir = Paths.get("uploads", "analysis");
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(anomalyApiTimeoutMs))
                .setReadTimeout(Duration.ofMillis(anomalyApiTimeoutMs))
                .build();

        startQueueProcessor();
    }

    /**
     * Queue an image for anomaly analysis.
     */
    @Transactional
    public AnalysisJob queueImageForAnalysis(Image image) {
        Optional<AnalysisJob> existingJob = analysisJobRepository.findByImage(image);
        if (existingJob.isPresent()) {
            logger.info("Analysis job already exists for image {}", image.getId());
            return existingJob.get();
        }

        AnalysisJob job = new AnalysisJob(image);
        Long queuedCount = analysisJobRepository.countByStatus(AnalysisJob.AnalysisStatus.QUEUED);
        job.setQueuePosition(queuedCount.intValue() + 1);

        AnalysisJob savedJob = analysisJobRepository.save(job);
        logger.info("Queued image {} for analysis with job ID {}", image.getId(), savedJob.getId());
        return savedJob;
    }

    public Optional<AnalysisJob> getAnalysisJobByImage(Image image) {
        return analysisJobRepository.findByImage(image);
    }

    public Optional<AnalysisJob> getAnalysisJobById(Long jobId) {
        return analysisJobRepository.findById(jobId);
    }

    public List<AnalysisJob> getAnalysisJobsByInspection(Long inspectionId) {
        return analysisJobRepository.findByInspectionId(inspectionId);
    }

    public QueueStatus getQueueStatus() {
        Long queuedCount = analysisJobRepository.countByStatus(AnalysisJob.AnalysisStatus.QUEUED);
        Long processingCount = analysisJobRepository.countByStatus(AnalysisJob.AnalysisStatus.PROCESSING);
        return new QueueStatus(queuedCount, processingCount);
    }

    private void startQueueProcessor() {
        CompletableFuture.runAsync(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    processNextJob();
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error in queue processor", e);
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }, executorService);
    }

    @Transactional
    private void processNextJob() {
        Optional<AnalysisJob> nextJob = analysisJobRepository.findNextQueuedJob();
        if (nextJob.isEmpty()) {
            return;
        }

        AnalysisJob job = nextJob.get();
        logger.info("Processing analysis job {}", job.getId());

        job.setStatus(AnalysisJob.AnalysisStatus.PROCESSING);
        job.setStartedAt(LocalDateTime.now());
        analysisJobRepository.save(job);

        try {
            AnalysisResult result = runAnomalyAnalysis(job.getImage());
            job.getImage().setFilePath(result.getBoxedImagePath());
            imageRepository.save(job.getImage());

            job.setResultJson(result.getJsonResult());
            job.setBoxedImagePath(result.getBoxedImagePath());
            job.setStatus(AnalysisJob.AnalysisStatus.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());

            logger.info("Completed analysis job {} with label: {}", job.getId(), result.getLabel());
        } catch (Exception e) {
            logger.error("Failed to process analysis job {}", job.getId(), e);
            job.setStatus(AnalysisJob.AnalysisStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(LocalDateTime.now());
        } finally {
            analysisJobRepository.save(job);
            updateQueuePositions();
        }
    }

    @Transactional
    private void updateQueuePositions() {
        List<AnalysisJob> queuedJobs = analysisJobRepository
                .findByStatusOrderByCreatedAtAsc(AnalysisJob.AnalysisStatus.QUEUED);
        for (int i = 0; i < queuedJobs.size(); i++) {
            AnalysisJob job = queuedJobs.get(i);
            job.setQueuePosition(i + 1);
            analysisJobRepository.save(job);
        }
    }

    /**
     * Call the Hugging Face Space API and persist returned assets locally.
     */
    private AnalysisResult runAnomalyAnalysis(Image image) throws IOException {
        String imageFilePath = image.getFilePath();
        if (imageFilePath.startsWith("/uploads/")) {
            imageFilePath = imageFilePath.substring("/uploads/".length());
        }

        Path originalImagePath = Paths.get("uploads", imageFilePath);
        if (!Files.exists(originalImagePath)) {
            throw new IOException("Original image file not found: " + originalImagePath.toAbsolutePath());
        }

        byte[] imageBytes = Files.readAllBytes(originalImagePath);
        String fileName = originalImagePath.getFileName().toString();
        String baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;

        double sensitivity = mlSettingsService.getDetectionSensitivity();
        double learningRate = mlSettingsService.getFeedbackLearningRate();
        ModelFeedbackService.FeedbackPayload feedbackPayload = modelFeedbackService.buildFeedbackPayload(learningRate);
        ModelFeedbackService.FeedbackSummary feedbackSummary = feedbackPayload.getSummary();

        logger.info("Using detection sensitivity: {}", sensitivity);
        logger.info("Feedback learning rate: {}", learningRate);
        if (feedbackPayload.hasAdjustments()) {
            logger.info("Applying {} label adjustments from feedback", feedbackSummary.getLabelFeedback().size());
            feedbackSummary.getLabelFeedback().stream().limit(3)
                    .forEach(f -> logger.info("  -> {}: adj={}, count_delta={}, areaRatio={}, conf_delta={}",
                            f.getLabel(),
                            f.getAdjustment(),
                            f.getAvgCountDelta(),
                            f.getAvgAreaRatio(),
                            f.getAvgConfidenceDelta()));
        } else {
            logger.info("No user feedback adjustments available yet.");
        }

        MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
        formData.add("file", asFileResource(imageBytes, fileName));
        formData.add("sensitivity", String.format(Locale.US, "%.4f", sensitivity));
        formData.add("feedback_json", feedbackPayload.toJsonString());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(formData, headers);

        logger.info("Calling remote anomaly API: {}", anomalyApiUrl);
        ResponseEntity<String> response = restTemplate.exchange(
                anomalyApiUrl,
                HttpMethod.POST,
                requestEntity,
                String.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IOException("Remote inference failed with status " + response.getStatusCode());
        }

        JsonNode payload = objectMapper.readTree(response.getBody());
        if (payload.hasNonNull("detail")) {
            throw new IOException("Remote inference error: " + payload.get("detail").asText());
        }

        String boxedBase64 = payload.path("boxed_image_base64").asText(null);
        String boxedExt = payload.path("boxed_image_ext").asText(".png");
        String label = payload.path("label").asText("unknown");
        String jsonText = payload.hasNonNull("json_text")
                ? payload.get("json_text").asText()
                : objectMapper.writeValueAsString(payload.path("json"));

        if (boxedBase64 == null || boxedBase64.isBlank()) {
            throw new IOException("Remote inference did not return a boxed image payload.");
        }

        byte[] boxedBytes;
        try {
            boxedBytes = Base64.getDecoder().decode(boxedBase64);
        } catch (IllegalArgumentException ex) {
            throw new IOException("Failed to decode boxed image from API", ex);
        }

        Files.createDirectories(analysisOutputDir);
        String boxedFileName = baseName + "_boxed" + boxedExt;
        Path boxedImagePath = analysisOutputDir.resolve(boxedFileName);
        Files.write(boxedImagePath, boxedBytes);

        String jsonFileName = baseName + ".json";
        Path jsonOutputPath = analysisOutputDir.resolve(jsonFileName);
        Files.writeString(jsonOutputPath, jsonText, StandardCharsets.UTF_8);

        String webBoxedPath = "/analysis/" + boxedFileName;
        String webJsonPath = "/analysis/" + jsonFileName;

        return new AnalysisResult(label, webBoxedPath, jsonText, webJsonPath);
    }

    private ByteArrayResource asFileResource(byte[] data, String filename) {
        return new ByteArrayResource(data) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    public static class QueueStatus {
        private final Long queuedCount;
        private final Long processingCount;

        public QueueStatus(Long queuedCount, Long processingCount) {
            this.queuedCount = queuedCount;
            this.processingCount = processingCount;
        }

        public Long getQueuedCount() {
            return queuedCount;
        }

        public Long getProcessingCount() {
            return processingCount;
        }
    }

    public static class AnalysisResult {
        private final String label;
        private final String boxedImagePath;
        private final String jsonResult;
        private final String jsonPath;

        public AnalysisResult(String label, String boxedImagePath, String jsonResult, String jsonPath) {
            this.label = label;
            this.boxedImagePath = boxedImagePath;
            this.jsonResult = jsonResult;
            this.jsonPath = jsonPath;
        }

        public String getLabel() {
            return label;
        }

        public String getBoxedImagePath() {
            return boxedImagePath;
        }

        public String getJsonResult() {
            return jsonResult;
        }

        public String getJsonPath() {
            return jsonPath;
        }
    }
}
