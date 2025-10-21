package com.projek.sipatik.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminApiController {

    // Endpoint khusus Admin - hanya bisa diakses oleh role ADMIN
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getAdminDashboard() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Selamat datang Admin");
        response.put("data", "Dashboard Admin SIPATIK");
        response.put("role", "ADMIN");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getAllUsers() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Daftar semua user");
        response.put("data", "List users akan ditampilkan di sini");
        response.put("access", "Admin only");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/users/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> activateUser(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "User dengan ID " + id + " berhasil diaktifkan");
        response.put("userId", id);
        response.put("action", "activate");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "User dengan ID " + id + " berhasil dihapus");
        response.put("userId", id);
        response.put("action", "delete");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/reports")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getReports() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Laporan sistem");
        response.put("data", "Data laporan akan ditampilkan di sini");
        response.put("type", "system_report");
        return ResponseEntity.ok(response);
    }
}



