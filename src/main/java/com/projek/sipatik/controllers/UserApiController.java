package com.projek.sipatik.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserApiController {

    // Endpoint khusus User - hanya bisa diakses oleh role USER
    @GetMapping("/profile")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> getUserProfile() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Profil user");
        response.put("data", "Data profil user akan ditampilkan di sini");
        response.put("role", "USER");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/infak-history")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> getInfakHistory() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Riwayat infak user");
        response.put("data", "Daftar infak yang pernah disetor");
        response.put("access", "User only");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/infak")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> setorInfak(@RequestBody Map<String, Object> infakData) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Infak berhasil disetor");
        response.put("data", infakData);
        response.put("action", "setor_infak");
        return ResponseEntity.ok(response);
    }

    @PutMapping("/profile")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> updateProfile(@RequestBody Map<String, Object> profileData) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Profil berhasil diperbarui");
        response.put("data", profileData);
        response.put("action", "update_profile");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/notifications")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> getUserNotifications() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Notifikasi user");
        response.put("data", "Daftar notifikasi akan ditampilkan di sini");
        response.put("type", "user_notifications");
        return ResponseEntity.ok(response);
    }
}



