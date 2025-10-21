package com.projek.sipatik.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/public")
public class PublicApiController {

    // Endpoint umum - bisa diakses oleh ADMIN maupun USER
    @GetMapping("/info")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<Map<String, Object>> getSystemInfo() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Informasi sistem SIPATIK");
        response.put("data", "Sistem Pencatatan Infak");
        response.put("version", "1.0.0");
        response.put("access", "ADMIN & USER");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Statistik umum");
        response.put("data", "Data statistik akan ditampilkan di sini");
        response.put("access", "ADMIN & USER");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/contact")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<Map<String, Object>> sendContact(@RequestBody Map<String, Object> contactData) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Pesan kontak berhasil dikirim");
        response.put("data", contactData);
        response.put("action", "send_contact");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/help")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<Map<String, Object>> getHelp() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Bantuan sistem");
        response.put("data", "Panduan penggunaan sistem");
        response.put("access", "ADMIN & USER");
        return ResponseEntity.ok(response);
    }
}



