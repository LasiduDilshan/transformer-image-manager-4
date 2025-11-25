package com.example.transformer_manager_backkend.controller;

import com.example.transformer_manager_backkend.entity.Admin;
import com.example.transformer_manager_backkend.entity.MaintenanceRecord;
import com.example.transformer_manager_backkend.entity.User;
import com.example.transformer_manager_backkend.repository.AdminRepository;
import com.example.transformer_manager_backkend.repository.UserRepository;
import com.example.transformer_manager_backkend.service.MaintenanceRecordPdfService;
import com.example.transformer_manager_backkend.service.MaintenanceRecordService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/maintenance-records")
@CrossOrigin(origins = "http://localhost:3000")
public class MaintenanceRecordController {

    private final MaintenanceRecordService maintenanceRecordService;
    private final MaintenanceRecordPdfService maintenanceRecordPdfService;
    private final AdminRepository adminRepository;
    private final UserRepository userRepository;

    public MaintenanceRecordController(MaintenanceRecordService maintenanceRecordService,
                                      MaintenanceRecordPdfService maintenanceRecordPdfService,
                                      AdminRepository adminRepository,
                                      UserRepository userRepository) {
        this.maintenanceRecordService = maintenanceRecordService;
        this.maintenanceRecordPdfService = maintenanceRecordPdfService;
        this.adminRepository = adminRepository;
        this.userRepository = userRepository;
    }

    @PostMapping("/inspection/{inspectionId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<MaintenanceRecord> createOrUpdateMaintenanceRecord(
            @PathVariable Long inspectionId,
            @RequestBody MaintenanceRecord recordData,
            Authentication authentication,
            Principal principal) {

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));

        MaintenanceRecord record;
        if (isAdmin) {
            Admin admin = adminRepository.findByUsername(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));
            record = maintenanceRecordService.createOrUpdateMaintenanceRecord(inspectionId, recordData, admin);
        } else {
            User user = userRepository.findByUsername(principal.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            record = maintenanceRecordService.createOrUpdateMaintenanceRecord(inspectionId, recordData, user);
        }

        return ResponseEntity.ok(record);
    }

    @GetMapping("/inspection/{inspectionId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<MaintenanceRecord> getMaintenanceRecordByInspection(@PathVariable Long inspectionId) {
        Optional<MaintenanceRecord> record = maintenanceRecordService.getMaintenanceRecordByInspectionId(inspectionId);
        return record.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/transformer/{transformerId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<List<MaintenanceRecord>> getMaintenanceRecordsByTransformer(@PathVariable Long transformerId) {
        return ResponseEntity.ok(maintenanceRecordService.getMaintenanceRecordsByTransformerId(transformerId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<MaintenanceRecord> getMaintenanceRecordById(@PathVariable Long id) {
        return ResponseEntity.ok(maintenanceRecordService.getMaintenanceRecordById(id));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<List<MaintenanceRecord>> getAllMaintenanceRecords() {
        return ResponseEntity.ok(maintenanceRecordService.getAllMaintenanceRecords());
    }

    @GetMapping("/{id}/export/pdf")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<byte[]> exportMaintenanceRecordPdf(@PathVariable Long id) {
        byte[] pdf = maintenanceRecordPdfService.generatePdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=maintenance_record_" + id + ".pdf")
                .body(pdf);
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<List<MaintenanceRecord>> getRecordsByStatus(@PathVariable String status) {
        return ResponseEntity.ok(maintenanceRecordService.getRecordsByStatus(status));
    }

    @GetMapping("/priority/{priority}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<List<MaintenanceRecord>> getRecordsByPriority(@PathVariable String priority) {
        return ResponseEntity.ok(maintenanceRecordService.getRecordsByPriority(priority));
    }

    @GetMapping("/pending-followups")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<List<MaintenanceRecord>> getPendingFollowUps() {
        return ResponseEntity.ok(maintenanceRecordService.getPendingFollowUps());
    }

    @PutMapping("/{id}/submit")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<MaintenanceRecord> submitRecord(
            @PathVariable Long id,
            Authentication authentication,
            Principal principal) {

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));

        MaintenanceRecord record;
        if (isAdmin) {
            Admin admin = adminRepository.findByUsername(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));
            record = maintenanceRecordService.submitRecord(id, admin);
        } else {
            User user = userRepository.findByUsername(principal.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            record = maintenanceRecordService.submitRecord(id, user);
        }

        return ResponseEntity.ok(record);
    }

    @PutMapping("/{id}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MaintenanceRecord> reviewRecord(
            @PathVariable Long id,
            @RequestParam String status,
            Principal principal) {

        Admin admin = adminRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        MaintenanceRecord record = maintenanceRecordService.reviewRecord(id, admin, status);
        return ResponseEntity.ok(record);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteMaintenanceRecord(@PathVariable Long id) {
        maintenanceRecordService.deleteMaintenanceRecord(id);
        return ResponseEntity.ok().build();
    }
}
