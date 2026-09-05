package com.projek.sipatik.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.projek.sipatik.models.Role;
import com.projek.sipatik.repositories.SetorInfakRepository;
import com.projek.sipatik.repositories.UserRepository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Informasi umum yang boleh dilihat admin maupun alumni.
 *
 * Endpoint POST /contact dihapus: ia menjawab "Pesan kontak berhasil dikirim" padahal
 * tidak ada tujuan pengiriman apa pun di aplikasi ini. Sementara /stats yang dulu hanya
 * mengembalikan kalimat placeholder sekarang menghitung angkanya dari database.
 */
@RestController
@RequestMapping("/api/public")
public class PublicApiController {

    private final UserRepository userRepository;
    private final SetorInfakRepository setorInfakRepository;

    public PublicApiController(UserRepository userRepository, SetorInfakRepository setorInfakRepository) {
        this.userRepository = userRepository;
        this.setorInfakRepository = setorInfakRepository;
    }

    @GetMapping("/info")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<Map<String, Object>> getSystemInfo() {
        Map<String, Object> response = new HashMap<>();
        response.put("nama", "SIPATIK");
        response.put("deskripsi", "Sistem Informasi Pencatatan Infak");
        response.put("organisasi", "Pemberdayaan Umat Berkelanjutan (PUB)");
        response.put("version", "1.0.0");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<Map<String, Object>> getStats() {
        LocalDate now = LocalDate.now();
        LocalDate awalBulan = now.withDayOfMonth(1);
        LocalDate akhirBulan = now.withDayOfMonth(now.lengthOfMonth());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("jumlahAlumni", userRepository.findByRole(Role.USER).size());
        response.put("totalInfakTerkonfirmasi", setorInfakRepository.totalInfakKeseluruhan());
        response.put("totalInfakBulanIni", setorInfakRepository.totalInfakBulanan(awalBulan, akhirBulan));
        response.put("periode", now.getMonthValue() + "/" + now.getYear());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/help")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<Map<String, Object>> getHelp() {
        Map<String, Object> response = new HashMap<>();
        response.put("alumni", "Setor infak lewat menu Setor Infak, lalu tunggu konfirmasi bendahara. "
                + "Status dan alasan penolakan bisa dilihat di menu Rekap Infak.");
        response.put("admin", "Konfirmasi atau tolak setoran di menu Daftar Infak, catat pengeluaran di menu "
                + "Pengeluaran, lalu isi saldo kas awal dan akhir di menu Laporan Keuangan.");
        response.put("kontak", "Hubungi bendahara PUB untuk pertanyaan terkait setoran.");
        return ResponseEntity.ok(response);
    }
}
