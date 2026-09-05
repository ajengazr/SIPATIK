package com.projek.sipatik.controllers;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.projek.sipatik.dto.EditPasswordRequest;
import com.projek.sipatik.dto.SetorInfakRequest;
import com.projek.sipatik.exception.FieldValidationException;
import com.projek.sipatik.models.SetorInfak;
import com.projek.sipatik.models.StatusInfak;
import com.projek.sipatik.models.Users;
import com.projek.sipatik.repositories.SetorInfakRepository;
import com.projek.sipatik.repositories.UserRepository;
import com.projek.sipatik.security.JwtUtil;
import com.projek.sipatik.services.InfakService;
import com.projek.sipatik.services.UserService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserService userService;
    @Autowired
    private InfakService infakService;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private SetorInfakRepository setorInfakRespository;

    @GetMapping("/dash-user")
    public String dashUser(HttpServletRequest request, Model model) {

        String token = extractTokenFromCookie(request);

        if (token == null || !jwtUtil.validateToken(token)) {
            return "redirect:/auth/login";
        }

        String email = jwtUtil.extractEmail(token);
        Users user = userRepository.findByEmail(email).orElseThrow();

        // 1. Total uang infak terkonfirmasi
        BigDecimal totalInfak = setorInfakRespository.totalInfakTerkonfirmasiByUser(user);

        // 2. Jumlah transaksi terkonfirmasi
        Long jumlahInfak = setorInfakRespository.jumlahInfakByUser(user);

        // 3. Infak terakhir yang sudah dikonfirmasi
        SetorInfak infakTerakhir = setorInfakRespository
                .findTopByUserAndStatusOrderByTanggalInfakDescIdDesc(user, StatusInfak.DIKONFIRMASI);
        Long nominalInfakTerakhir = (infakTerakhir != null) ? infakTerakhir.getNominal() : 0L;

        // 4. Status bulan berjalan
        boolean sudahInfakBulanIni = false;
        if (infakTerakhir != null && infakTerakhir.getTanggalInfak() != null) {
            LocalDate tanggal = infakTerakhir.getTanggalInfak();
            LocalDate now = LocalDate.now();
            sudahInfakBulanIni = tanggal.getMonthValue() == now.getMonthValue() && tanggal.getYear() == now.getYear();
        }

        // 5. Setoran yang masih menunggu / ditolak, supaya alumni tahu tindak lanjutnya
        boolean adaMenunggu = setorInfakRespository.existsByUserAndStatus(user, StatusInfak.MENUNGGU);
        SetorInfak ditolakTerakhir = setorInfakRespository
                .findTopByUserAndStatusOrderByTanggalInfakDescIdDesc(user, StatusInfak.DITOLAK);

        model.addAttribute("nama", user.getNama());
        model.addAttribute("angkatan", user.getAngkatan());
        model.addAttribute("totalInfak", totalInfak);
        model.addAttribute("jumlahInfak", jumlahInfak);
        model.addAttribute("infakTerakhir", nominalInfakTerakhir);
        model.addAttribute("statusBulanIni", sudahInfakBulanIni ? "Sudah Infak" : "Belum Infak");
        model.addAttribute("adaSetoranMenunggu", adaMenunggu);
        model.addAttribute("setoranDitolak", ditolakTerakhir);
        return "html/user/dash";
    }

    private String extractTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null)
            return null;
        for (Cookie cookie : request.getCookies()) {
            if (cookie.getName().equals("jwt")) {
                return cookie.getValue();
            }
        }
        return null;
    }

    @GetMapping("/form-setor-infak")
    public String formSetorInfak(HttpServletRequest request, Model model) {
        SetorInfakRequest form = new SetorInfakRequest();
        form.setTanggalInfak(LocalDate.now());
        model.addAttribute("setorInfakRequest", form);
        model.addAttribute("hariIni", LocalDate.now());

        String token = extractTokenFromCookie(request);
        if (token != null) {
            try {
                Users user = userService.getUserFromToken(token);
                model.addAttribute("minimalInfak", infakService.getMinimalInfak(user));
            } catch (RuntimeException e) {
                // Tarif tidak tersedia (mis. angkatan di luar rentang) tidak boleh
                // menghalangi form tampil; validasinya tetap jalan saat submit.
                model.addAttribute("minimalInfakError", e.getMessage());
            }
        }
        return "html/user/setor-infak";
    }

    @PostMapping("/setor-infak")
    public String prosesInfak(@Valid @ModelAttribute SetorInfakRequest request,
            BindingResult bindingResult,
            @CookieValue("jwt") String token,
            Model model) {

        if (bindingResult.hasErrors()) {
            Map<String, String> fieldErrors = new HashMap<>();
            bindingResult.getFieldErrors().forEach(err -> fieldErrors.put(err.getField(), err.getDefaultMessage()));
            return kembaliKeForm(model, request, fieldErrors);
        }

        try {
            Users user = userService.getUserFromToken(token);
            infakService.simpanInfakAlumni(user, request);

            model.addAttribute("message",
                    "Alhamdulillah... Semoga infaknya berkah. Tunggu infaknya dikonfirmasi yaaa");
            return "html/user/pop-up";
        } catch (FieldValidationException e) {
            Map<String, String> fieldErrors = new HashMap<>();
            fieldErrors.put(e.getField(), e.getMessage());
            return kembaliKeForm(model, request, fieldErrors);
        } catch (IOException e) {
            Map<String, String> fieldErrors = new HashMap<>();
            fieldErrors.put("buktiTransfer", "Gagal upload file. Silakan coba lagi.");
            return kembaliKeForm(model, request, fieldErrors);
        }
    }

    private String kembaliKeForm(Model model, SetorInfakRequest request, Map<String, String> fieldErrors) {
        model.addAttribute("setorInfakRequest", request);
        model.addAttribute("fieldErrors", fieldErrors);
        model.addAttribute("hariIni", LocalDate.now());
        return "html/user/setor-infak";
    }

    /**
     * Riwayat setoran alumni.
     *
     * Sekarang menampilkan semua status, bukan hanya yang terkonfirmasi, supaya alumni
     * bisa melihat setoran yang masih menunggu dan alasan setoran yang ditolak.
     */
    @GetMapping("/rekap-infak")
    public String readRekapInfak(HttpServletRequest request, Model model) {
        String token = extractTokenFromCookie(request);
        Users user = userService.getUserFromToken(token);

        List<SetorInfak> semua = infakService.riwayatLengkap(user);
        List<SetorInfak> terkonfirmasi = semua.stream()
                .filter(SetorInfak::isDikonfirmasi)
                .toList();

        model.addAttribute("daftarInfak", semua);
        model.addAttribute("daftarInfakTerkonfirmasi", terkonfirmasi);
        model.addAttribute("totalTerkonfirmasi", setorInfakRespository.totalInfakTerkonfirmasiByUser(user));
        return "html/user/rekap-infak";
    }

    @GetMapping("profil")
    public String profil(HttpServletRequest request, Model model) {
        String token = extractTokenFromCookie(request);
        Users user = userService.getUserFromToken(token);

        model.addAttribute("user", user);
        return "html/user/profil";
    }

    @GetMapping("/edit-password")
    public String formEditPassword(Model model) {
        model.addAttribute("passwordRequest", new EditPasswordRequest());
        return "html/user/form-edit-password";
    }

    /**
     * Ganti kata sandi.
     *
     * Dipetakan ke POST karena form HTML tidak bisa mengirim PUT dan filter _method
     * tidak aktif, sehingga versi @PutMapping sebelumnya selalu berakhir 405.
     */
    @PostMapping("/proses-edit-password")
    public String editPassword(@ModelAttribute("passwordRequest") EditPasswordRequest editPasswordRequest,
            HttpServletRequest request,
            Model model) {
        String token = extractTokenFromCookie(request);
        Users user = userService.getUserFromToken(token);

        try {
            userService.updatePassword(user, editPasswordRequest);
            model.addAttribute("message", "Password berhasil diubah!");
            return "html/user/pop-up";
        } catch (FieldValidationException e) {
            model.addAttribute("passwordRequest", editPasswordRequest);
            model.addAttribute("errorField", e.getField());
            model.addAttribute("errorMessage", e.getMessage());
            return "html/user/form-edit-password";
        }
    }

    @GetMapping("/tentang")
    public String tentang(Model model) {
        model.addAttribute("pageTitle", "Tentang Kami - SIPATIK");
        model.addAttribute("activePage", "tentang");
        return "html/user/tentang";
    }
}
