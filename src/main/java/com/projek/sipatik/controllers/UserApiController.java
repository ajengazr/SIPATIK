package com.projek.sipatik.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.projek.sipatik.dto.AlumniResponse;
import com.projek.sipatik.dto.SetorInfakResponse;
import com.projek.sipatik.exception.UnauthorizedException;
import com.projek.sipatik.models.Users;
import com.projek.sipatik.services.InfakService;

import java.util.HashMap;
import java.util.Map;

/**
 * Endpoint baca-saja untuk alumni yang sedang login.
 *
 * Versi sebelumnya berisi lima endpoint yang seluruhnya mengembalikan kalimat
 * placeholder. Yang paling berbahaya adalah POST /api/user/infak, yang menjawab
 * "Infak berhasil disetor" tanpa menyimpan apa pun ke database. Endpoint palsu itu
 * dihapus; penyetoran infak lewat API ditangani POST /api/setor-infak, dan pembaruan
 * profil belum ada fiturnya sehingga tidak diklaim ada di sini.
 */
@RestController
@RequestMapping("/api/user")
public class UserApiController {

    private final InfakService infakService;

    public UserApiController(InfakService infakService) {
        this.infakService = infakService;
    }

    @GetMapping("/profile")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> getUserProfile(@AuthenticationPrincipal Users user) {
        Users alumni = wajibLogin(user);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", AlumniResponse.from(alumni, InfakService.isAngkatanDidukung(alumni.getAngkatan())));
        try {
            response.put("minimalInfak", infakService.getMinimalInfak(alumni));
        } catch (RuntimeException e) {
            response.put("minimalInfak", null);
            response.put("catatan", e.getMessage());
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/infak-history")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> getInfakHistory(@AuthenticationPrincipal Users user) {
        Users alumni = wajibLogin(user);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", infakService.riwayatLengkap(alumni).stream()
                .map(SetorInfakResponse::from)
                .toList());
        return ResponseEntity.ok(response);
    }

    private Users wajibLogin(Users user) {
        if (user == null) {
            throw new UnauthorizedException("Token tidak valid atau tidak ditemukan");
        }
        return user;
    }
}
