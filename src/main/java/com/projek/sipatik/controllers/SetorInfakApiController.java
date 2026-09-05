package com.projek.sipatik.controllers;

import com.projek.sipatik.dto.SetorInfakRequest;
import com.projek.sipatik.dto.SetorInfakResponse;
import com.projek.sipatik.exception.BadRequestException;
import com.projek.sipatik.exception.ForbiddenException;
import com.projek.sipatik.exception.ResourceNotFoundException;
import com.projek.sipatik.exception.UnauthorizedException;
import com.projek.sipatik.models.Role;
import com.projek.sipatik.models.SetorInfak;
import com.projek.sipatik.models.Users;
import com.projek.sipatik.services.InfakService;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/setor-infak")
public class SetorInfakApiController {

    @Autowired
    private InfakService infakService;

    /**
     * Setor infak lewat REST.
     *
     * Penyimpanan didelegasikan ke InfakService supaya aturan minimal nominal per
     * angkatan, batas tanggal, jenis file bukti, dan pengecekan setoran ganda berlaku
     * sama seperti jalur form web. Versi lama menyimpan langsung ke repository dengan
     * pengecekan nominal lebih dari 0 saja, sehingga batas minimal bisa dilewati.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> setorInfak(
            @Valid @ModelAttribute SetorInfakRequest request,
            @AuthenticationPrincipal Users user) {

        Users pemohon = wajibLogin(user);

        try {
            SetorInfak tersimpan = infakService.simpanInfakAlumni(pemohon, request);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Infak berhasil disetor, menunggu konfirmasi admin");
            response.put("data", SetorInfakResponse.from(tersimpan));
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            throw new BadRequestException("Gagal menyimpan file bukti transfer: " + e.getMessage());
        }
    }

    /**
     * Detail satu setoran.
     *
     * Alumni hanya boleh melihat setorannya sendiri, dan yang dikembalikan adalah DTO,
     * bukan entity yang membawa serta data akun pemiliknya.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getSetorInfak(@PathVariable Long id,
            @AuthenticationPrincipal Users user) {

        Users pemohon = wajibLogin(user);
        SetorInfak infak = infakService.ambil(id);

        boolean pemilik = infak.getUser() != null && infak.getUser().getId().equals(pemohon.getId());
        if (!pemilik && pemohon.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Anda tidak berhak melihat setoran milik alumni lain");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", SetorInfakResponse.from(infak));
        return ResponseEntity.ok(response);
    }

    /** Riwayat setoran milik alumni yang sedang login. */
    @GetMapping("/saya")
    public ResponseEntity<Map<String, Object>> riwayatSaya(@AuthenticationPrincipal Users user) {
        Users pemohon = wajibLogin(user);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", infakService.riwayatLengkap(pemohon).stream()
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

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException e) {
        return ResponseEntity.status(404).body(Map.of("success", false, "error", e.getMessage()));
    }
}
