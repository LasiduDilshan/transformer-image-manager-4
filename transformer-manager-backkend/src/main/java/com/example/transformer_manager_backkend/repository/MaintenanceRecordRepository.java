package com.example.transformer_manager_backkend.repository;

import com.example.transformer_manager_backkend.entity.MaintenanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MaintenanceRecordRepository extends JpaRepository<MaintenanceRecord, Long> {
    
    Optional<MaintenanceRecord> findByInspectionId(Long inspectionId);
    
    @Query("SELECT mr FROM MaintenanceRecord mr WHERE mr.inspection.transformerRecord.id = :transformerRecordId ORDER BY mr.createdAt DESC")
    List<MaintenanceRecord> findByTransformerRecordId(Long transformerRecordId);
    
    @Query("SELECT mr FROM MaintenanceRecord mr WHERE mr.transformerStatus = :status ORDER BY mr.createdAt DESC")
    List<MaintenanceRecord> findByTransformerStatus(String status);
    
    @Query("SELECT mr FROM MaintenanceRecord mr WHERE mr.maintenancePriority = :priority ORDER BY mr.createdAt DESC")
    List<MaintenanceRecord> findByMaintenancePriority(String priority);
    
    @Query("SELECT mr FROM MaintenanceRecord mr WHERE mr.requiresFollowUp = true AND mr.followUpDate IS NOT NULL ORDER BY mr.followUpDate ASC")
    List<MaintenanceRecord> findPendingFollowUps();
}
