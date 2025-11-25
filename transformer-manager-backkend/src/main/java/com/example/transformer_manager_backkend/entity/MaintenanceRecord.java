package com.example.transformer_manager_backkend.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "maintenance_records")
public class MaintenanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "inspection_id", nullable = false, unique = true)
    @JsonIgnoreProperties({ "maintenanceRecord" })
    private Inspection inspection;

    // Inspector Information
    @Column(length = 255)
    private String inspectorName;

    @Column(length = 100)
    private String inspectorId;

    @Column(length = 255)
    private String inspectorEmail;

    // Transformer Status
    @Column(length = 50)
    private String transformerStatus; // OK, NEEDS_MAINTENANCE, URGENT_ATTENTION

    // Electrical Readings
    private Double voltagePhaseA;
    private Double voltagePhaseB;
    private Double voltagePhaseC;
    
    private Double currentPhaseA;
    private Double currentPhaseB;
    private Double currentPhaseC;

    private Double powerFactor;
    private Double frequency;
    private Double ambientTemperature;
    private Double oilTemperature;
    private Double windingTemperature;

    // Oil Analysis
    @Column(length = 50)
    private String oilLevel; // NORMAL, LOW, CRITICAL
    
    @Column(length = 50)
    private String oilColor; // CLEAR, LIGHT_BROWN, DARK_BROWN, BLACK

    @Column(columnDefinition = "TEXT")
    private String oilAnalysisRemarks;

    // Visual Inspection
    @Column(columnDefinition = "TEXT")
    private String coolingSystemCondition;

    @Column(columnDefinition = "TEXT")
    private String bushingCondition;

    @Column(columnDefinition = "TEXT")
    private String tankCondition;

    @Column(columnDefinition = "TEXT")
    private String gaugesCondition;

    // Maintenance Actions
    @Column(columnDefinition = "TEXT")
    private String detectedAnomalies; // JSON or comma-separated list

    @Column(columnDefinition = "TEXT")
    private String correctiveActions;

    @Column(columnDefinition = "TEXT")
    private String recommendedAction;

    @Column(length = 50)
    private String maintenancePriority; // LOW, MEDIUM, HIGH, CRITICAL

    private LocalDateTime scheduledMaintenanceDate;

    // Additional Information
    @Column(columnDefinition = "TEXT")
    private String engineerNotes;

    @Column(columnDefinition = "TEXT")
    private String additionalRemarks;

    @Column(length = 50)
    private String weatherCondition; // CLEAR, CLOUDY, RAINY, STORMY

    @Column(length = 100)
    private String loadCondition; // NO_LOAD, LIGHT_LOAD, NORMAL_LOAD, HEAVY_LOAD, OVERLOAD

    // Parts and Materials
    @Column(columnDefinition = "TEXT")
    private String partsReplaced;

    @Column(columnDefinition = "TEXT")
    private String materialsUsed;

    // Follow-up Information
    private Boolean requiresFollowUp;
    private LocalDateTime followUpDate;

    @Column(columnDefinition = "TEXT")
    private String followUpNotes;

    // Safety and Compliance
    @Column(columnDefinition = "TEXT")
    private String safetyObservations;

    private Boolean complianceCheck;

    @Column(columnDefinition = "TEXT")
    private String complianceNotes;

    // Timestamps
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column(length = 50)
    private String recordStatus; // DRAFT, SUBMITTED, REVIEWED, APPROVED

    @ManyToOne
    @JoinColumn(name = "submitted_by_admin")
    private Admin submittedByAdmin;

    @ManyToOne
    @JoinColumn(name = "submitted_by_user")
    private User submittedByUser;

    @ManyToOne
    @JoinColumn(name = "reviewed_by_admin")
    private Admin reviewedByAdmin;

    private LocalDateTime reviewedAt;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Inspection getInspection() {
        return inspection;
    }

    public void setInspection(Inspection inspection) {
        this.inspection = inspection;
    }

    public String getInspectorName() {
        return inspectorName;
    }

    public void setInspectorName(String inspectorName) {
        this.inspectorName = inspectorName;
    }

    public String getInspectorId() {
        return inspectorId;
    }

    public void setInspectorId(String inspectorId) {
        this.inspectorId = inspectorId;
    }

    public String getInspectorEmail() {
        return inspectorEmail;
    }

    public void setInspectorEmail(String inspectorEmail) {
        this.inspectorEmail = inspectorEmail;
    }

    public String getTransformerStatus() {
        return transformerStatus;
    }

    public void setTransformerStatus(String transformerStatus) {
        this.transformerStatus = transformerStatus;
    }

    public Double getVoltagePhaseA() {
        return voltagePhaseA;
    }

    public void setVoltagePhaseA(Double voltagePhaseA) {
        this.voltagePhaseA = voltagePhaseA;
    }

    public Double getVoltagePhaseB() {
        return voltagePhaseB;
    }

    public void setVoltagePhaseB(Double voltagePhaseB) {
        this.voltagePhaseB = voltagePhaseB;
    }

    public Double getVoltagePhaseC() {
        return voltagePhaseC;
    }

    public void setVoltagePhaseC(Double voltagePhaseC) {
        this.voltagePhaseC = voltagePhaseC;
    }

    public Double getCurrentPhaseA() {
        return currentPhaseA;
    }

    public void setCurrentPhaseA(Double currentPhaseA) {
        this.currentPhaseA = currentPhaseA;
    }

    public Double getCurrentPhaseB() {
        return currentPhaseB;
    }

    public void setCurrentPhaseB(Double currentPhaseB) {
        this.currentPhaseB = currentPhaseB;
    }

    public Double getCurrentPhaseC() {
        return currentPhaseC;
    }

    public void setCurrentPhaseC(Double currentPhaseC) {
        this.currentPhaseC = currentPhaseC;
    }

    public Double getPowerFactor() {
        return powerFactor;
    }

    public void setPowerFactor(Double powerFactor) {
        this.powerFactor = powerFactor;
    }

    public Double getFrequency() {
        return frequency;
    }

    public void setFrequency(Double frequency) {
        this.frequency = frequency;
    }

    public Double getAmbientTemperature() {
        return ambientTemperature;
    }

    public void setAmbientTemperature(Double ambientTemperature) {
        this.ambientTemperature = ambientTemperature;
    }

    public Double getOilTemperature() {
        return oilTemperature;
    }

    public void setOilTemperature(Double oilTemperature) {
        this.oilTemperature = oilTemperature;
    }

    public Double getWindingTemperature() {
        return windingTemperature;
    }

    public void setWindingTemperature(Double windingTemperature) {
        this.windingTemperature = windingTemperature;
    }

    public String getOilLevel() {
        return oilLevel;
    }

    public void setOilLevel(String oilLevel) {
        this.oilLevel = oilLevel;
    }

    public String getOilColor() {
        return oilColor;
    }

    public void setOilColor(String oilColor) {
        this.oilColor = oilColor;
    }

    public String getOilAnalysisRemarks() {
        return oilAnalysisRemarks;
    }

    public void setOilAnalysisRemarks(String oilAnalysisRemarks) {
        this.oilAnalysisRemarks = oilAnalysisRemarks;
    }

    public String getCoolingSystemCondition() {
        return coolingSystemCondition;
    }

    public void setCoolingSystemCondition(String coolingSystemCondition) {
        this.coolingSystemCondition = coolingSystemCondition;
    }

    public String getBushingCondition() {
        return bushingCondition;
    }

    public void setBushingCondition(String bushingCondition) {
        this.bushingCondition = bushingCondition;
    }

    public String getTankCondition() {
        return tankCondition;
    }

    public void setTankCondition(String tankCondition) {
        this.tankCondition = tankCondition;
    }

    public String getGaugesCondition() {
        return gaugesCondition;
    }

    public void setGaugesCondition(String gaugesCondition) {
        this.gaugesCondition = gaugesCondition;
    }

    public String getDetectedAnomalies() {
        return detectedAnomalies;
    }

    public void setDetectedAnomalies(String detectedAnomalies) {
        this.detectedAnomalies = detectedAnomalies;
    }

    public String getCorrectiveActions() {
        return correctiveActions;
    }

    public void setCorrectiveActions(String correctiveActions) {
        this.correctiveActions = correctiveActions;
    }

    public String getRecommendedAction() {
        return recommendedAction;
    }

    public void setRecommendedAction(String recommendedAction) {
        this.recommendedAction = recommendedAction;
    }

    public String getMaintenancePriority() {
        return maintenancePriority;
    }

    public void setMaintenancePriority(String maintenancePriority) {
        this.maintenancePriority = maintenancePriority;
    }

    public LocalDateTime getScheduledMaintenanceDate() {
        return scheduledMaintenanceDate;
    }

    public void setScheduledMaintenanceDate(LocalDateTime scheduledMaintenanceDate) {
        this.scheduledMaintenanceDate = scheduledMaintenanceDate;
    }

    public String getEngineerNotes() {
        return engineerNotes;
    }

    public void setEngineerNotes(String engineerNotes) {
        this.engineerNotes = engineerNotes;
    }

    public String getAdditionalRemarks() {
        return additionalRemarks;
    }

    public void setAdditionalRemarks(String additionalRemarks) {
        this.additionalRemarks = additionalRemarks;
    }

    public String getWeatherCondition() {
        return weatherCondition;
    }

    public void setWeatherCondition(String weatherCondition) {
        this.weatherCondition = weatherCondition;
    }

    public String getLoadCondition() {
        return loadCondition;
    }

    public void setLoadCondition(String loadCondition) {
        this.loadCondition = loadCondition;
    }

    public String getPartsReplaced() {
        return partsReplaced;
    }

    public void setPartsReplaced(String partsReplaced) {
        this.partsReplaced = partsReplaced;
    }

    public String getMaterialsUsed() {
        return materialsUsed;
    }

    public void setMaterialsUsed(String materialsUsed) {
        this.materialsUsed = materialsUsed;
    }

    public Boolean getRequiresFollowUp() {
        return requiresFollowUp;
    }

    public void setRequiresFollowUp(Boolean requiresFollowUp) {
        this.requiresFollowUp = requiresFollowUp;
    }

    public LocalDateTime getFollowUpDate() {
        return followUpDate;
    }

    public void setFollowUpDate(LocalDateTime followUpDate) {
        this.followUpDate = followUpDate;
    }

    public String getFollowUpNotes() {
        return followUpNotes;
    }

    public void setFollowUpNotes(String followUpNotes) {
        this.followUpNotes = followUpNotes;
    }

    public String getSafetyObservations() {
        return safetyObservations;
    }

    public void setSafetyObservations(String safetyObservations) {
        this.safetyObservations = safetyObservations;
    }

    public Boolean getComplianceCheck() {
        return complianceCheck;
    }

    public void setComplianceCheck(Boolean complianceCheck) {
        this.complianceCheck = complianceCheck;
    }

    public String getComplianceNotes() {
        return complianceNotes;
    }

    public void setComplianceNotes(String complianceNotes) {
        this.complianceNotes = complianceNotes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getRecordStatus() {
        return recordStatus;
    }

    public void setRecordStatus(String recordStatus) {
        this.recordStatus = recordStatus;
    }

    public Admin getSubmittedByAdmin() {
        return submittedByAdmin;
    }

    public void setSubmittedByAdmin(Admin submittedByAdmin) {
        this.submittedByAdmin = submittedByAdmin;
    }

    public User getSubmittedByUser() {
        return submittedByUser;
    }

    public void setSubmittedByUser(User submittedByUser) {
        this.submittedByUser = submittedByUser;
    }

    public Admin getReviewedByAdmin() {
        return reviewedByAdmin;
    }

    public void setReviewedByAdmin(Admin reviewedByAdmin) {
        this.reviewedByAdmin = reviewedByAdmin;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
}
