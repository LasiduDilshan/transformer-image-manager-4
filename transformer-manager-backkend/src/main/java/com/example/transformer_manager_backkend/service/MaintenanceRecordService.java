package com.example.transformer_manager_backkend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.transformer_manager_backkend.entity.Admin;
import com.example.transformer_manager_backkend.entity.Inspection;
import com.example.transformer_manager_backkend.entity.MaintenanceRecord;
import com.example.transformer_manager_backkend.entity.User;
import com.example.transformer_manager_backkend.repository.InspectionRepository;
import com.example.transformer_manager_backkend.repository.MaintenanceRecordRepository;

@Service
public class MaintenanceRecordService {

    private final MaintenanceRecordRepository maintenanceRecordRepository;
    private final InspectionRepository inspectionRepository;

    public MaintenanceRecordService(MaintenanceRecordRepository maintenanceRecordRepository,
                                   InspectionRepository inspectionRepository) {
        this.maintenanceRecordRepository = maintenanceRecordRepository;
        this.inspectionRepository = inspectionRepository;
    }

    @Transactional
    public MaintenanceRecord createOrUpdateMaintenanceRecord(Long inspectionId, MaintenanceRecord recordData, User user) {
        Inspection inspection = inspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new RuntimeException("Inspection not found"));

        // Check if a maintenance record already exists for this inspection
        Optional<MaintenanceRecord> existingRecord = maintenanceRecordRepository.findByInspectionId(inspectionId);

        MaintenanceRecord record;
        if (existingRecord.isPresent()) {
            record = existingRecord.get();
            updateRecordFields(record, recordData);
        } else {
            record = new MaintenanceRecord();
            record.setInspection(inspection);
            record.setSubmittedByUser(user);
            record.setRecordStatus("DRAFT");
            updateRecordFields(record, recordData);
        }

        return maintenanceRecordRepository.save(record);
    }

    @Transactional
    public MaintenanceRecord createOrUpdateMaintenanceRecord(Long inspectionId, MaintenanceRecord recordData, Admin admin) {
        Inspection inspection = inspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new RuntimeException("Inspection not found"));

        Optional<MaintenanceRecord> existingRecord = maintenanceRecordRepository.findByInspectionId(inspectionId);

        MaintenanceRecord record;
        if (existingRecord.isPresent()) {
            record = existingRecord.get();
            updateRecordFields(record, recordData);
        } else {
            record = new MaintenanceRecord();
            record.setInspection(inspection);
            record.setSubmittedByAdmin(admin);
            record.setRecordStatus("DRAFT");
            updateRecordFields(record, recordData);
        }

        return maintenanceRecordRepository.save(record);
    }

    private void updateRecordFields(MaintenanceRecord record, MaintenanceRecord data) {
        // Inspector Information
        if (data.getInspectorName() != null) record.setInspectorName(data.getInspectorName());
        if (data.getInspectorId() != null) record.setInspectorId(data.getInspectorId());
        if (data.getInspectorEmail() != null) record.setInspectorEmail(data.getInspectorEmail());

        // Transformer Status
        if (data.getTransformerStatus() != null) record.setTransformerStatus(data.getTransformerStatus());

        // Electrical Readings
        if (data.getVoltagePhaseA() != null) record.setVoltagePhaseA(data.getVoltagePhaseA());
        if (data.getVoltagePhaseB() != null) record.setVoltagePhaseB(data.getVoltagePhaseB());
        if (data.getVoltagePhaseC() != null) record.setVoltagePhaseC(data.getVoltagePhaseC());
        if (data.getCurrentPhaseA() != null) record.setCurrentPhaseA(data.getCurrentPhaseA());
        if (data.getCurrentPhaseB() != null) record.setCurrentPhaseB(data.getCurrentPhaseB());
        if (data.getCurrentPhaseC() != null) record.setCurrentPhaseC(data.getCurrentPhaseC());
        if (data.getPowerFactor() != null) record.setPowerFactor(data.getPowerFactor());
        if (data.getFrequency() != null) record.setFrequency(data.getFrequency());
        if (data.getAmbientTemperature() != null) record.setAmbientTemperature(data.getAmbientTemperature());
        if (data.getOilTemperature() != null) record.setOilTemperature(data.getOilTemperature());
        if (data.getWindingTemperature() != null) record.setWindingTemperature(data.getWindingTemperature());

        // Oil Analysis
        if (data.getOilLevel() != null) record.setOilLevel(data.getOilLevel());
        if (data.getOilColor() != null) record.setOilColor(data.getOilColor());
        if (data.getOilAnalysisRemarks() != null) record.setOilAnalysisRemarks(data.getOilAnalysisRemarks());

        // Visual Inspection
        if (data.getCoolingSystemCondition() != null) record.setCoolingSystemCondition(data.getCoolingSystemCondition());
        if (data.getBushingCondition() != null) record.setBushingCondition(data.getBushingCondition());
        if (data.getTankCondition() != null) record.setTankCondition(data.getTankCondition());
        if (data.getGaugesCondition() != null) record.setGaugesCondition(data.getGaugesCondition());

        // Maintenance Actions
        if (data.getDetectedAnomalies() != null) record.setDetectedAnomalies(data.getDetectedAnomalies());
        if (data.getCorrectiveActions() != null) record.setCorrectiveActions(data.getCorrectiveActions());
        if (data.getRecommendedAction() != null) record.setRecommendedAction(data.getRecommendedAction());
        if (data.getMaintenancePriority() != null) record.setMaintenancePriority(data.getMaintenancePriority());
        if (data.getScheduledMaintenanceDate() != null) record.setScheduledMaintenanceDate(data.getScheduledMaintenanceDate());

        // Additional Information
        if (data.getEngineerNotes() != null) record.setEngineerNotes(data.getEngineerNotes());
        if (data.getAdditionalRemarks() != null) record.setAdditionalRemarks(data.getAdditionalRemarks());
        if (data.getWeatherCondition() != null) record.setWeatherCondition(data.getWeatherCondition());
        if (data.getLoadCondition() != null) record.setLoadCondition(data.getLoadCondition());

        // Parts and Materials
        if (data.getPartsReplaced() != null) record.setPartsReplaced(data.getPartsReplaced());
        if (data.getMaterialsUsed() != null) record.setMaterialsUsed(data.getMaterialsUsed());

        // Follow-up Information
        if (data.getRequiresFollowUp() != null) record.setRequiresFollowUp(data.getRequiresFollowUp());
        if (data.getFollowUpDate() != null) record.setFollowUpDate(data.getFollowUpDate());
        if (data.getFollowUpNotes() != null) record.setFollowUpNotes(data.getFollowUpNotes());

        // Safety and Compliance
        if (data.getSafetyObservations() != null) record.setSafetyObservations(data.getSafetyObservations());
        if (data.getComplianceCheck() != null) record.setComplianceCheck(data.getComplianceCheck());
        if (data.getComplianceNotes() != null) record.setComplianceNotes(data.getComplianceNotes());

        // Status
        if (data.getRecordStatus() != null) record.setRecordStatus(data.getRecordStatus());
    }

    public Optional<MaintenanceRecord> getMaintenanceRecordByInspectionId(Long inspectionId) {
        return maintenanceRecordRepository.findByInspectionId(inspectionId);
    }

    public List<MaintenanceRecord> getMaintenanceRecordsByTransformerId(Long transformerRecordId) {
        return maintenanceRecordRepository.findByTransformerRecordId(transformerRecordId);
    }

    public MaintenanceRecord getMaintenanceRecordById(Long id) {
        return maintenanceRecordRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Maintenance record not found"));
    }

    public List<MaintenanceRecord> getAllMaintenanceRecords() {
        return maintenanceRecordRepository.findAll();
    }

    public List<MaintenanceRecord> getRecordsByStatus(String status) {
        return maintenanceRecordRepository.findByTransformerStatus(status);
    }

    public List<MaintenanceRecord> getRecordsByPriority(String priority) {
        return maintenanceRecordRepository.findByMaintenancePriority(priority);
    }

    public List<MaintenanceRecord> getPendingFollowUps() {
        return maintenanceRecordRepository.findPendingFollowUps();
    }

    @Transactional
    public MaintenanceRecord submitRecord(Long recordId, User user) {
        MaintenanceRecord record = getMaintenanceRecordById(recordId);
        record.setRecordStatus("SUBMITTED");
        record.setSubmittedByUser(user);
        return maintenanceRecordRepository.save(record);
    }

    @Transactional
    public MaintenanceRecord submitRecord(Long recordId, Admin admin) {
        MaintenanceRecord record = getMaintenanceRecordById(recordId);
        record.setRecordStatus("SUBMITTED");
        record.setSubmittedByAdmin(admin);
        return maintenanceRecordRepository.save(record);
    }

    @Transactional
    public MaintenanceRecord reviewRecord(Long recordId, Admin admin, String status) {
        MaintenanceRecord record = getMaintenanceRecordById(recordId);
        record.setRecordStatus(status); // REVIEWED or APPROVED
        record.setReviewedByAdmin(admin);
        record.setReviewedAt(LocalDateTime.now());
        return maintenanceRecordRepository.save(record);
    }

    @Transactional
    public void deleteMaintenanceRecord(Long id) {
        maintenanceRecordRepository.deleteById(id);
    }
}
