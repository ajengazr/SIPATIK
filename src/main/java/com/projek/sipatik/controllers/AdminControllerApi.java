package com.projek.sipatik.controllers;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.projek.sipatik.models.Pengeluaran;
import com.projek.sipatik.models.Users;
import com.projek.sipatik.repositories.PengeluaranRepository;
import com.projek.sipatik.repositories.UserRepository;
import com.projek.sipatik.services.AuthService;

@RestController
@RequestMapping("/admin/api")
public class AdminControllerApi {
    private static final Logger log = LoggerFactory.getLogger(AdminControllerApi.class);

    private final PengeluaranRepository pengeluaranRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    

    public AdminControllerApi(PengeluaranRepository pengeluaranRepository, UserRepository userRepository, AuthService authService) {
        this.pengeluaranRepository = pengeluaranRepository;
        this.userRepository = userRepository;
        this.authService = authService;
    }

    @GetMapping("/pengeluaran")
    public List<Pengeluaran> getPengeluaran(
            @RequestParam(required = false) String jenis,
            @RequestParam(required = false) Integer bulan,
            @RequestParam(required = false) Integer tahun) {

        List<Pengeluaran> semua = pengeluaranRepository.findAll();

        return semua.stream()
                .filter(p -> {
                    if (jenis == null || jenis.isEmpty())
                        return true;
                    if (p.getJenis() == null)
                        return false;
                    return p.getJenis().name().equalsIgnoreCase(jenis);
                })
                .filter(p -> {
                    if (bulan == null)
                        return true;
                    if (p.getTanggalPengeluaran() == null)
                        return false;
                    return p.getTanggalPengeluaran().getMonthValue() == bulan;
                })
                .filter(p -> {
                    if (tahun == null)
                        return true;
                    if (p.getTanggalPengeluaran() == null)
                        return false;
                    return p.getTanggalPengeluaran().getYear() == tahun;
                })
                .collect(Collectors.toList());
    }

    @PostMapping("/resend-token")
    public ResponseEntity<?> resendToken(@RequestParam String email) {
        try {
            log.debug("Resend token requested for email: {}", email);
            Users user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User tidak ditemukan"));

            authService.resendToken(email, user);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Token baru sudah dikirim ke email Anda. Silahkan input token."
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }
}
