package com.example.transformer_manager_backkend.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Value("${app.anomaly.model.path:C:/Users/pasir/Desktop/Github/transformer-image-manager-3/automatic-anamoly-detection/Model_Inference}")
    private String modelPath;

    @Value("${app.anomaly.venv.path:/mnt/c/Users/pasir/Desktop/Github/transformer-image-manager-3/automatic-anamoly-detection/.venv}")
    private String venvPath;

    @Value("${app.anomaly.temp.dir:./temp/anomaly-analysis}")
    private String tempDir;

    public AnomalyAnalysisService(AnalysisJobRepository analysisJobRepository, ImageRepository imageRepository,
            MLSettingsService mlSettingsService, ModelFeedbackService modelFeedbackService) {
        this.analysisJobRepository = analysisJobRepository;
        this.imageRepository = imageRepository;
        this.mlSettingsService = mlSettingsService;
        this.modelFeedbackService = modelFeedbackService;
        this.objectMapper = new ObjectMapper();
        this.executorService = Executors.newSingleThreadExecutor();

        // Start the queue processor
        startQueueProcessor();
    }

    /**
     * Queue an image for anomaly analysis
     */
    @Transactional
    public AnalysisJob queueImageForAnalysis(Image image) {
        // Check if analysis already exists for this image
        Optional<AnalysisJob> existingJob = analysisJobRepository.findByImage(image);
        if (existingJob.isPresent()) {
            logger.info("Analysis job already exists for image {}", image.getId());
            return existingJob.get();
        }

        AnalysisJob job = new AnalysisJob(image);

        // Set queue position
        Long queuedCount = analysisJobRepository.countByStatus(AnalysisJob.AnalysisStatus.QUEUED);
        job.setQueuePosition(queuedCount.intValue() + 1);

        AnalysisJob savedJob = analysisJobRepository.save(job);
        logger.info("Queued image {} for analysis with job ID {}", image.getId(), savedJob.getId());

        return savedJob;
    }

    /**
     * Get analysis job by image
     */
    public Optional<AnalysisJob> getAnalysisJobByImage(Image image) {
        return analysisJobRepository.findByImage(image);
    }

    /**
     * Get analysis job by ID
     */
    public Optional<AnalysisJob> getAnalysisJobById(Long jobId) {
        return analysisJobRepository.findById(jobId);
    }

    /**
     * Get all analysis jobs for an inspection
     */
    public List<AnalysisJob> getAnalysisJobsByInspection(Long inspectionId) {
        return analysisJobRepository.findByInspectionId(inspectionId);
    }

    /**
     * Get queue status
     */
    public QueueStatus getQueueStatus() {
        Long queuedCount = analysisJobRepository.countByStatus(AnalysisJob.AnalysisStatus.QUEUED);
        Long processingCount = analysisJobRepository.countByStatus(AnalysisJob.AnalysisStatus.PROCESSING);

        return new QueueStatus(queuedCount, processingCount);
    }

    /**
     * Start the background queue processor
     */
    private void startQueueProcessor() {
        CompletableFuture.runAsync(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    processNextJob();
                    Thread.sleep(5000); // Check every 5 seconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error in queue processor", e);
                    try {
                        Thread.sleep(10000); // Wait 10 seconds before retrying
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }, executorService);
    }

    /**
     * Process the next job in the queue
     */
    @Transactional
    private void processNextJob() {
        Optional<AnalysisJob> nextJob = analysisJobRepository.findNextQueuedJob();
        if (nextJob.isEmpty()) {
            return;
        }

        AnalysisJob job = nextJob.get();
        logger.info("Processing analysis job {}", job.getId());

        // Update status to processing
        job.setStatus(AnalysisJob.AnalysisStatus.PROCESSING);
        job.setStartedAt(LocalDateTime.now());
        analysisJobRepository.save(job);

        try {
            // Perform the actual analysis
            AnalysisResult result = runAnomalyAnalysis(job.getImage());

            // IMPORTANT: Update the image's file path to point to the boxed image
            job.getImage().setFilePath(result.getBoxedImagePath());

            // Save the updated image to the database
            imageRepository.save(job.getImage());

            // Update job with results
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

    /**
     * Update queue positions after a job is completed
     */
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
     * Run the actual anomaly analysis using the WSL script
     */
    private AnalysisResult runAnomalyAnalysis(Image image) throws IOException, InterruptedException {
        // Get the original image path and fix it
        String imageFilePath = image.getFilePath();
        if (imageFilePath.startsWith("/uploads/")) {
            imageFilePath = imageFilePath.substring("/uploads/".length()); // Remove /uploads/
        }

        // The actual file path should be in uploads directory (relative to backend
        // working directory)
        Path originalImagePath = Paths.get("uploads", imageFilePath);

        if (!Files.exists(originalImagePath)) {
            throw new IOException("Original image file not found: " + originalImagePath.toAbsolutePath());
        }

        // Create unique temporary directories for this analysis job within the project
        String jobId = "job_" + image.getId() + "_" + System.currentTimeMillis();
        Path tempJobPath = Paths.get(tempDir, jobId);
        Path inputDir = tempJobPath.resolve("input");
        Path outputDir = tempJobPath.resolve("output");

        // Clean up and create fresh directories
        if (Files.exists(tempJobPath)) {
            deleteDirectory(tempJobPath);
        }
        Files.createDirectories(inputDir);
        Files.createDirectories(outputDir);

        // Copy image to temp input directory
        String fileName = originalImagePath.getFileName().toString();
        Path tempImagePath = inputDir.resolve(fileName);
        Files.copy(originalImagePath, tempImagePath, StandardCopyOption.REPLACE_EXISTING);

        logger.info("Copied image from {} to {}", originalImagePath, tempImagePath);

        // Verify the copied file exists
        if (!Files.exists(tempImagePath)) {
            throw new IOException("Failed to copy image to temp directory: " + tempImagePath);
        }
        logger.info("Verified image exists at: {}", tempImagePath.toAbsolutePath());

        // Get the current working directory and convert to WSL path for relative paths
        // to work
        Path currentDir = Paths.get(".").toAbsolutePath().normalize();
        String projectRootWSL = convertToWSLPath(currentDir.toString());

        // Convert relative paths to be relative from project root
        Path relativeInputDir = currentDir.relativize(inputDir.toAbsolutePath());
        Path relativeOutputDir = currentDir.relativize(outputDir.toAbsolutePath());

        String wslInputDir = projectRootWSL + "/" + relativeInputDir.toString().replace('\\', '/');
        String wslOutputDir = projectRootWSL + "/" + relativeOutputDir.toString().replace('\\', '/');

        // Load current ML tuning knobs
        double sensitivity = mlSettingsService.getDetectionSensitivity();
        double learningRate = mlSettingsService.getFeedbackLearningRate();
        ModelFeedbackService.FeedbackPayload feedbackPayload = modelFeedbackService.buildFeedbackPayload(learningRate);
        ModelFeedbackService.FeedbackSummary feedbackSummary = feedbackPayload.getSummary();

        Path feedbackFile = tempJobPath.resolve("feedback_adjustments.json");
        Files.writeString(feedbackFile, feedbackPayload.toJsonString(), StandardCharsets.UTF_8);
        Path relativeFeedbackPath = currentDir.relativize(feedbackFile.toAbsolutePath());
        String wslFeedbackPath = projectRootWSL + "/" + relativeFeedbackPath.toString().replace('\\', '/');

        logger.info("Using detection sensitivity: {}", sensitivity);
        logger.info("Feedback learning rate: {}", learningRate);
        logger.info("Feedback adjustment global bias: {}", feedbackSummary.getGlobalAdjustment());
        if (feedbackPayload.hasAdjustments()) {
            logger.info("Applying {} label adjustments from feedback", feedbackSummary.getLabelFeedback().size());
            feedbackSummary.getLabelFeedback().stream().limit(3)
                    .forEach(f -> logger.info("  -> {}: adj={}, countΔ={}, areaRatio={}, confΔ={}",
                            f.getLabel(),
                            f.getAdjustment(),
                            f.getAvgCountDelta(),
                            f.getAvgAreaRatio(),
                            f.getAvgConfidenceDelta()));
        } else {
            logger.info("No user feedback adjustments available yet.");
        }
        logger.info("Project root (Windows): {}", currentDir);
        logger.info("Project root (WSL): {}", projectRootWSL);
        logger.info("Input directory (Windows): {}", inputDir.toAbsolutePath());
        logger.info("Input directory (WSL): {}", wslInputDir);
        logger.info("Output directory (Windows): {}", outputDir.toAbsolutePath());
        logger.info("Output directory (WSL): {}", wslOutputDir);
        logger.info("Feedback payload (WSL path): {}", wslFeedbackPath);

        // Prepare WSL command with sensitivity and feedback adjustments
        String wslCommand = String.format(
                "wsl --cd \"/mnt/c/Users/pasir/Desktop/Github/transformer-image-manager-3/automatic-anamoly-detection/Model_Inference\" -- ./run_inference.sh --venv \"%s\" --input \"%s\" --outdir \"%s\" --sensitivity %.2f --feedback \"%s\"",
                venvPath,
                wslInputDir,
                wslOutputDir,
                sensitivity,
                wslFeedbackPath);
        logger.info("Running WSL command: {}", wslCommand);

        // Execute the command
        ProcessBuilder processBuilder = new ProcessBuilder("cmd", "/c", wslCommand);
        processBuilder.directory(new File(modelPath));
        Process process = processBuilder.start();

        // Capture output
        StringBuilder output = new StringBuilder();
        StringBuilder error = new StringBuilder();

        try (BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

            String line;
            while ((line = outputReader.readLine()) != null) {
                output.append(line).append("\n");
                logger.debug("Analysis output: {}", line);
            }

            while ((line = errorReader.readLine()) != null) {
                error.append(line).append("\n");
                if (!line.trim().isEmpty()) {
                    logger.warn("Analysis error: {}", line);
                }
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Analysis failed with exit code " + exitCode + ": " + error.toString());
        }

        // Parse results
        AnalysisResult result = parseAnalysisResults(outputDir, fileName, image);

        // Clean up temp directories
        try {
            deleteDirectory(tempJobPath);
        } catch (Exception e) {
            logger.warn("Failed to clean up temp directory: {}", tempJobPath, e);
        }

        return result;
    }

    /**
     * Delete directory recursively
     */
    private void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.walk(directory)
                    .sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            logger.warn("Failed to delete: {}", path, e);
                        }
                    });
        }
    }

    /**
     * Parse the analysis results from the output directory
     */
    private AnalysisResult parseAnalysisResults(Path outputDir, String fileName, Image originalImage)
            throws IOException {
        String baseName = fileName.substring(0, fileName.lastIndexOf('.'));

        // Look for JSON result file
        Path jsonFile = outputDir.resolve("boxed").resolve(baseName + ".json");
        if (!Files.exists(jsonFile)) {
            throw new IOException("JSON result file not found: " + jsonFile);
        }

        // Look for boxed image
        Path boxedImageDir = outputDir.resolve("boxed");
        Optional<Path> boxedImagePath = Files.list(boxedImageDir)
                .filter(path -> path.getFileName().toString().startsWith(baseName + "_boxed"))
                .findFirst();

        if (boxedImagePath.isEmpty()) {
            throw new IOException("Boxed image not found for: " + baseName);
        }

        // Read JSON results
        String jsonContent = Files.readString(jsonFile);
        JsonNode jsonNode = objectMapper.readTree(jsonContent);

        String label = jsonNode.get("label").asText();

        // Create uploads/analysis directory if it doesn't exist
        Path analysisDir = Paths.get("uploads", "analysis");
        Files.createDirectories(analysisDir);

        // Copy boxed image to uploads directory, replacing the original
        String boxedFileName = baseName + "_boxed" + getFileExtension(fileName);
        Path targetBoxedPath = analysisDir.resolve(boxedFileName);
        Files.copy(boxedImagePath.get(), targetBoxedPath, StandardCopyOption.REPLACE_EXISTING);

        // Copy JSON file to uploads directory
        String jsonFileName = baseName + ".json";
        Path targetJsonPath = analysisDir.resolve(jsonFileName);
        Files.copy(jsonFile, targetJsonPath, StandardCopyOption.REPLACE_EXISTING);

        // Return the web-accessible path (without /uploads prefix since FileController
        // adds it)
        String webBoxedPath = "/analysis/" + boxedFileName;
        String webJsonPath = "/analysis/" + jsonFileName;

        return new AnalysisResult(label, webBoxedPath, jsonContent, webJsonPath);
    }

    /**
     * Get file extension
     */
    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot) : ".jpg";
    }

    /**
     * Convert Windows path to WSL path
     */
    private String convertToWSLPath(String windowsPath) {
        // Convert C:\path\to\file to /mnt/c/path/to/file
        if (windowsPath.length() >= 3 && windowsPath.charAt(1) == ':') {
            char drive = Character.toLowerCase(windowsPath.charAt(0));
            String path = windowsPath.substring(3).replace('\\', '/');
            return "/mnt/" + drive + "/" + path;
        }
        return windowsPath.replace('\\', '/');
    }

    /**
     * Queue status DTO
     */
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

    /**
     * Analysis result DTO
     */
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
