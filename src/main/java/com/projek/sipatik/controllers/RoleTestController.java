package com.projek.sipatik.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class RoleTestController {

    // Endpoint untuk testing role access
    @GetMapping("/role-check")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<Map<String, Object>> checkRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String role = auth.getAuthorities().iterator().next().getAuthority();
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Role check berhasil");
        response.put("currentRole", role);
        response.put("username", auth.getName());
        response.put("authenticated", auth.isAuthenticated());
        return ResponseEntity.ok(response);
    }

    // Test endpoint yang memerlukan ADMIN
    @GetMapping("/admin-only")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> adminOnlyTest() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Anda berhasil mengakses endpoint admin-only");
        response.put("access", "ADMIN only");
        response.put("status", "success");
        return ResponseEntity.ok(response);
    }

    // Test endpoint yang memerlukan USER
    @GetMapping("/user-only")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> userOnlyTest() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Anda berhasil mengakses endpoint user-only");
        response.put("access", "USER only");
        response.put("status", "success");
        return ResponseEntity.ok(response);
    }
}
