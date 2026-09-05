package com.projek.sipatik.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.projek.sipatik.dto.AlumniResponse;
import com.projek.sipatik.models.Role;
import com.projek.sipatik.repositories.UserRepository;
import com.projek.sipatik.services.AdminService;
import com.projek.sipatik.services.InfakService;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Endpoint baca-saja untuk admin.
 *
 * Versi sebelumnya seluruhnya placeholder. Dua di antaranya berbahaya karena melapor
 * sukses tanpa melakukan apa pun: POST /users/{id}/activate (tidak ada konsep aktif
 * pada model) dan DELETE /users/{id} (menyatakan alumni terhapus padahal tidak).
 * Keduanya dihapus. Penghapusan alumni punya aturan sendiri dan ditangani
 * POST /admin/alumni/hapus/{id}, yang menolak bila alumni masih punya catatan infak.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminApiController {

    private final AdminService adminService;
    private final UserRepository userRepository;

    public AdminApiController(AdminService adminService, UserRepository userRepository) {
        this.adminService = adminService;
        this.userRepository = userRepository;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getAdminDashboard() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", adminService.getDashboardData());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getAllUsers(
            @RequestParam(value = "angkatan", required = false) Long angkatan) {

        var alumni = (angkatan == null)
                ? userRepository.findByRoleOrderByNamaAsc(Role.USER)
                : userRepository.findByRoleAndAngkatanOrderByNamaAsc(Role.USER, angkatan);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("total", alumni.size());
        response.put("data", alumni.stream()
                .map(u -> AlumniResponse.from(u, InfakService.isAngkatanDidukung(u.getAngkatan())))
                .toList());
        return ResponseEntity.ok(response);
    }

    /** Laporan kas satu periode. Default periode berjalan. */
    @GetMapping("/reports")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getReports(
            @RequestParam(value = "bulan", required = false) Integer bulan,
            @RequestParam(value = "tahun", required = false) Integer tahun) {

        LocalDate now = LocalDate.now();
        int b = (bulan != null && bulan >= 1 && bulan <= 12) ? bulan : now.getMonthValue();
        int t = (tahun != null) ? tahun : now.getYear();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("bulan", b);
        response.put("tahun", t);
        response.put("data", adminService.buildDataLaporanKas(t, b));
        return ResponseEntity.ok(response);
    }
}
