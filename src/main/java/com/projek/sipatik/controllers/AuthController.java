package com.projek.sipatik.controllers;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import com.projek.sipatik.dto.LoginRequest;
import com.projek.sipatik.dto.LoginResponse;
import com.projek.sipatik.dto.UserRequest;
import com.projek.sipatik.exception.FieldValidationException;
import com.projek.sipatik.models.Role;
import com.projek.sipatik.models.Users;
import com.projek.sipatik.repositories.UserRepository;
import com.projek.sipatik.services.AuthService;
import com.projek.sipatik.services.AuthService.PasswordResetAccount;
import com.projek.sipatik.services.AuthService.PasswordResetGrant;
import com.projek.sipatik.services.AlumniInvitationService;
import com.projek.sipatik.services.AlumniInvitationService.InvitationAccount;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/auth")
public class AuthController {
    private static final String PASSWORD_RESET_COOKIE = "sipatik_reset";
    private static final String ACTIVATION_COOKIE = "sipatik_activation";
    private static final long JWT_COOKIE_MAX_AGE_SECONDS = 3600;
    private static final long PASSWORD_RESET_COOKIE_MAX_AGE_SECONDS = 600;
    private static final long ACTIVATION_COOKIE_MAX_AGE_SECONDS = 1800;
    
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AuthService authService;
    @Autowired
    private AlumniInvitationService invitationService;
    @Value("${app.security.cookie-secure:true}")
    private boolean secureJwtCookie;

    /** Alias lama dipertahankan; landing utama tersedia di root melalui LandingController. */
    @GetMapping({"", "/"})
    public String landingPageAlias() {
        return "html/landing-page";
    }

    @GetMapping("/cek")
    public String halamanCek(Model model) {
        model.addAttribute("angkatanList", userRepository.findDistinctAngkatanByRole(Role.USER));
        model.addAttribute("user", new Users());
        return "html/auth/form-select";
    }

    @PostMapping("/cek-user")
    public String cekUser(@ModelAttribute Users user, RedirectAttributes redirect, Model model) {

        Optional<Users> optional = userRepository.findByNamaAndAngkatanAndRole(
                user.getNama(), user.getAngkatan(), Role.USER);

        if (optional.isPresent()) {
            Users existingUser = optional.get();
            if (isRegistrationCandidate(existingUser)) {
                redirect.addFlashAttribute("error",
                        "Akun ini belum aktif. Minta tautan undangan kepada admin.");
                return "redirect:/auth/cek";
            } else if (isRegistered(existingUser)) {
                redirect.addAttribute("nama", existingUser.getNama());
                redirect.addAttribute("angkatan", existingUser.getAngkatan());
                return "redirect:/auth/login";
            }
            redirect.addFlashAttribute("error",
                    "Data akun belum lengkap. Hubungi admin untuk memperbaikinya.");
            return "redirect:/auth/cek";
        } else {
            redirect.addFlashAttribute("error", "Data alumni tidak ditemukan.");
            return "redirect:/auth/cek";
        }
    }

    @GetMapping("/aktivasi")
    public RedirectView mulaiAktivasi(
            @RequestParam(name = "token", required = false) String token,
            HttpServletResponse response,
            RedirectAttributes redirect) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Referrer-Policy", "no-referrer");

        RedirectView target;
        if (invitationService.findActive(token).isEmpty()) {
            clearActivationCookie(response);
            redirect.addFlashAttribute("error", "Undangan tidak valid atau telah berakhir.");
            target = new RedirectView("/auth/cek", true);
        } else {
            addActivationCookie(response, token, ACTIVATION_COOKIE_MAX_AGE_SECONDS);
            target = new RedirectView("/auth/daftar", true);
        }
        target.setStatusCode(HttpStatus.SEE_OTHER);
        return target;
    }

    @GetMapping("/daftar")
    public String halamanDaftar(@ModelAttribute("user") UserRequest userRequest,
            @CookieValue(name = ACTIVATION_COOKIE, required = false) String activationToken,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirect) {
        if (!isiKonteksAktivasi(activationToken, model)) {
            clearActivationCookie(response);
            redirect.addFlashAttribute("error",
                    "Sesi aktivasi tidak valid. Minta tautan undangan baru kepada admin.");
            return "redirect:/auth/cek";
        }
        return "html/auth/daftar";
    }

    @PostMapping("/pendaftaran")
    public String prosesForm(
            @Valid @ModelAttribute("user") UserRequest userRequest,
            @CookieValue(name = ACTIVATION_COOKIE, required = false) String activationToken,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirect) {

        Map<String, String> fieldError = new HashMap<>();
        try {
            Users registered = invitationService.register(activationToken, userRequest, fieldError);

            if (fieldError.isEmpty()) {
                clearActivationCookie(response);
                redirect.addAttribute("message", "Pendaftaran berhasil. Silakan masuk menggunakan akun Anda.");
                return "redirect:/auth/sukses";
            }
            model.addAttribute("namaAlumni", registered.getNama());
            model.addAttribute("angkatanAlumni", registered.getAngkatan());
        } catch (DataIntegrityViolationException e) {
            // Constraint database adalah pertahanan terakhir untuk dua registrasi
            // paralel yang memilih email sama pada akun alumni berbeda.
            fieldError.put("email", "Email sudah digunakan");
            // Transaksi service sudah rollback, jadi undangan tetap dapat dipakai untuk
            // mencoba email lain. Pulihkan identitas tepercaya dari token server-side;
            // jangan merender form anonim bila undangannya keburu dicabut/kedaluwarsa.
            if (!isiKonteksAktivasi(activationToken, model)) {
                clearActivationCookie(response);
                redirect.addFlashAttribute("error",
                        "Sesi aktivasi tidak valid. Minta tautan undangan baru kepada admin.");
                return "redirect:/auth/cek";
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            clearActivationCookie(response);
            redirect.addFlashAttribute("error",
                    "Undangan tidak valid atau telah berakhir.");
            return "redirect:/auth/cek";
        }

        model.addAttribute("fieldErrors", fieldError);
        return "html/auth/daftar";
    }

    @GetMapping("/sukses")
    public String sukses(@RequestParam String message,
            Model model) {
        model.addAttribute("message", message);
        return "html/sukses";
    }

    @GetMapping("/login")
    public String halamanLogin(
            @RequestParam(name = "nama", required = false) String nama,
            @RequestParam(name = "angkatan", required = false) Long angkatan,
            Model model) {

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setNama(nama);
        loginRequest.setAngkatan(angkatan);
        model.addAttribute("loginRequest", loginRequest);
        model.addAttribute("nama", nama);
        model.addAttribute("angkatan", angkatan);

        return "html/auth/login";
    }

    @PostMapping("/proses-login")
    public String processLogin(@Valid @ModelAttribute("loginRequest") LoginRequest loginRequest,
            BindingResult result,
            HttpServletResponse response,
            RedirectAttributes redirect) {

        if (result.hasErrors()) {
            return "html/auth/login";
        }

        try {
            LoginResponse loginResponse = authService.validateLogin(
                    loginRequest.getEmail(),
                    loginRequest.getPassword());

            // Akun admin wajib melewati alur /auth-adm yang memverifikasi token email.
            // Tanpa pemeriksaan role ini, kredensial admin yang benar dapat dipakai di
            // form alumni dan langsung memperoleh JWT tanpa langkah kedua.
            if (loginResponse.getRole() != Role.USER) {
                throw new FieldValidationException("email",
                        "Akun admin harus masuk melalui halaman login admin.");
            }

            // Simpan token di cookie (tanpa session)
            addJwtCookie(response, loginResponse.getToken(), JWT_COOKIE_MAX_AGE_SECONDS);

            return "redirect:/user/dash-user";
        } catch (FieldValidationException e) {
            result.rejectValue(e.getField(), "invalid", e.getMessage());

            return "html/auth/login";
        }
    }

    @PostMapping("/form-otp")
    public String formOtp(
            @RequestParam(name = "nama", required = false) String nama,
            @RequestParam(name = "angkatan", required = false) Long angkatan,
            RedirectAttributes redirect,
            Model model) {
        if (nama == null || nama.isBlank() || angkatan == null) {
            redirect.addFlashAttribute("error", "User gagal ditemukan.");
            return "redirect:/auth/cek";
        }

        Optional<Users> param = userRepository.findByNamaAndAngkatanAndRole(nama, angkatan, Role.USER);

        if (param.isPresent()) {
            Users user = param.get();

            if (user.getEmail() != null && !user.getEmail().isEmpty()) {
                model.addAttribute("userId", user.getId());
                model.addAttribute("nama", nama);
                model.addAttribute("angkatan", angkatan);
                try {
                    authService.createAndSendOtp(user);
                } catch (IllegalStateException e) {
                    model.addAttribute("error", e.getMessage());
                }
                return "html/auth/form-otp";
            } else {
                redirect.addFlashAttribute("error", "Reset password tidak dapat diproses untuk akun tersebut.");
                return "redirect:/auth/cek";
            }
        } else {
            return "redirect:/auth/cek";
        }
    }

    @PostMapping("/verifikasi-otp")
    public String verifikasiOtp(
            @RequestParam("userId") Long userId,
            @RequestParam("otp") String otp,
            RedirectAttributes redirect,
            HttpServletResponse response) {
        try {
            PasswordResetGrant grant = authService.verifyOtpAndIssuePasswordReset(userId, otp);
            addPasswordResetCookie(response, grant.token(), PASSWORD_RESET_COOKIE_MAX_AGE_SECONDS);
            return "redirect:/auth/form-lupa-password";
        } catch (IllegalStateException e) {
            clearPasswordResetCookie(response);
            redirect.addFlashAttribute("error", "Kode OTP tidak valid atau telah kedaluwarsa.");
            return "redirect:/auth/cek";
        }
    }

    @GetMapping("/form-lupa-password")
    public String formLupaPassword(
            @CookieValue(name = PASSWORD_RESET_COOKIE, required = false) String resetToken,
            HttpServletResponse response,
            RedirectAttributes redirect,
            Model model) {
        Optional<PasswordResetAccount> account = authService.findActivePasswordReset(resetToken);
        if (account.isEmpty()) {
            clearPasswordResetCookie(response);
            redirect.addFlashAttribute("error", "Sesi reset password tidak valid atau telah berakhir.");
            return "redirect:/auth/cek";
        }

        PasswordResetAccount trustedAccount = account.get();
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setNama(trustedAccount.nama());
        loginRequest.setAngkatan(trustedAccount.angkatan());
        loginRequest.setEmail(trustedAccount.email());
        model.addAttribute("loginRequest", loginRequest);
        return "html/auth/lupa-password";
    }

    @PostMapping("/proses-lupa-password")
    public String prosesLupaPassword(
            @RequestParam("password") String newPassword,
            @CookieValue(name = PASSWORD_RESET_COOKIE, required = false) String resetToken,
            HttpServletResponse response,
            RedirectAttributes redirect) {
        if (resetToken == null || resetToken.isBlank()) {
            clearPasswordResetCookie(response);
            redirect.addFlashAttribute("error", "Sesi reset password tidak valid atau telah berakhir.");
            return "redirect:/auth/cek";
        }

        try {
            PasswordResetAccount account = authService.consumePasswordReset(resetToken, newPassword);
            clearPasswordResetCookie(response);
            redirect.addAttribute("nama", account.nama());
            redirect.addAttribute("angkatan", account.angkatan());
            return "redirect:/auth/login";
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/auth/form-lupa-password";
        } catch (IllegalStateException e) {
            clearPasswordResetCookie(response);
            redirect.addFlashAttribute("error", "Sesi reset password tidak valid atau telah berakhir.");
            return "redirect:/auth/cek";
        }
    }

    @GetMapping("/logout")
    public String logout(
            HttpServletResponse response,
            RedirectAttributes redirect) {
        addJwtCookie(response, "", 0);

        redirect.addAttribute("message", "Berhasil Keluar");
        redirect.addAttribute("redirectUrl", "/auth/cek");
        return "redirect:/auth/sukses";
    }

    private void addJwtCookie(HttpServletResponse response, String value, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from("jwt", value)
                .httpOnly(true)
                .secure(secureJwtCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void addPasswordResetCookie(HttpServletResponse response, String value, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(PASSWORD_RESET_COOKIE, value)
                .httpOnly(true)
                .secure(secureJwtCookie)
                .sameSite("Lax")
                .path("/auth")
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearPasswordResetCookie(HttpServletResponse response) {
        addPasswordResetCookie(response, "", 0);
    }

    private void addActivationCookie(HttpServletResponse response, String value, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(ACTIVATION_COOKIE, value == null ? "" : value)
                .httpOnly(true)
                .secure(secureJwtCookie)
                // Lax tetap mengirim cookie pada redirect top-level dari tautan yang
                // dibuka lewat WhatsApp/email. POST final tetap dilindungi CSRF.
                .sameSite("Lax")
                .path("/auth")
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearActivationCookie(HttpServletResponse response) {
        addActivationCookie(response, "", 0);
    }

    private boolean isiKonteksAktivasi(String activationToken, Model model) {
        Optional<InvitationAccount> account = invitationService.findActive(activationToken);
        if (account.isEmpty()) {
            return false;
        }
        model.addAttribute("namaAlumni", account.get().nama());
        model.addAttribute("angkatanAlumni", account.get().angkatan());
        return true;
    }

    private boolean isRegistrationCandidate(Users user) {
        return user != null
                && user.getRole() == Role.USER
                && isBlank(user.getEmail())
                && isBlank(user.getPassword())
                && isBlank(user.getNomorHp());
    }

    private boolean isRegistered(Users user) {
        return user != null
                && user.getRole() == Role.USER
                && !isBlank(user.getEmail())
                && !isBlank(user.getPassword())
                && !isBlank(user.getNomorHp());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
