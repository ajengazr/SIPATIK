package com.projek.sipatik.controllers;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.projek.sipatik.dto.LoginRequest;
import com.projek.sipatik.dto.LoginResponse;
import com.projek.sipatik.dto.UserRequest;
import com.projek.sipatik.exception.FieldValidationException;
import com.projek.sipatik.models.OtpToken;
import com.projek.sipatik.models.Role;
import com.projek.sipatik.models.Users;
import com.projek.sipatik.repositories.OtpTokenRepository;
import com.projek.sipatik.repositories.UserRepository;
import com.projek.sipatik.services.AuthService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AuthService authService;
    @Autowired
    private OtpTokenRepository otpTokenRepository;

    @GetMapping("/")
    public String landingPage() {
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

        Optional<Users> optional = userRepository.findByNamaAndAngkatan(user.getNama(), user.getAngkatan());

        if (optional.isPresent()) {
            Users existingUser = optional.get();
            if (existingUser.getEmail() == null || existingUser.getPassword() == null
                    || existingUser.getNomorHp() == null) {
                redirect.addFlashAttribute("id", existingUser.getId());
                redirect.addFlashAttribute("user", new UserRequest());
                return "redirect:/auth/daftar";
            } else {
                return "redirect:/auth/login?nama=" + existingUser.getNama()
                        + "&angkatan=" + existingUser.getAngkatan();
            }
        } else {
            model.addAttribute("message", "Data tidak ditemukan");
            return "not-found";
        }
    }

    @GetMapping("/daftar")
    public String halamanDaftar(@ModelAttribute("user") UserRequest userRequest,
            @ModelAttribute("id") Long userId,
            Model model) {
        if (!model.containsAttribute("user")) {
            model.addAttribute("user", new UserRequest());
        }
        model.addAttribute("userId", userId);
        return "html/auth/daftar";
    }

    @PostMapping("/pendaftaran")
    public String prosesForm(
            @RequestParam("id") Long id,
            @Valid @ModelAttribute("user") UserRequest userRequest,
            Model model) {

        Map<String, String> fieldError = new HashMap<>();
        try {
            authService.validasiDaftar(id, userRequest, fieldError);

            // kalo gaada error
            if (fieldError.isEmpty()) {
                return "redirect:/auth/sukses?message=Pendaftaran Anda telah berhasil. Silakan cek email untuk detail lebih lanjut";
            }
        } catch (Exception e) {
            // jika err0r fatal
            model.addAttribute("error", e.getMessage());
            return "html/not-found";
        }

        model.addAttribute("fieldErrors", fieldError);
        model.addAttribute("userId", id);
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
            @RequestParam(name = "angkatan", required = false) String angkatan,
            Model model) {

        model.addAttribute("loginRequest", new LoginRequest());
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

            // Simpan token di cookie (tanpa session)
            Cookie cookie = new Cookie("jwt", loginResponse.getToken());
            cookie.setHttpOnly(true);
            cookie.setMaxAge(86400);
            cookie.setPath("/");
            response.addCookie(cookie);

            return "redirect:/user/dash-user";
        } catch (FieldValidationException e) {
            result.rejectValue(e.getField(), "invalid", e.getMessage());

            return "html/auth/login";
        }
    }

    @GetMapping("/form-otp")
    public String formOtp(
            @RequestParam(name = "nama", required = false) String nama,
            @RequestParam(name = "angkatan", required = false) Long angkatan,
            RedirectAttributes redirect,
            Model model) {
        Optional<Users> param = userRepository.findByNamaAndAngkatan(nama, angkatan);

        if (param.isPresent()) {
            Users user = param.get();

            if (user.getEmail() != null && !user.getEmail().isEmpty()) {
                if (nama == null || nama.isBlank() || angkatan == null) {
                    redirect.addFlashAttribute("error", "User Gagal Ditemukan.");
                    return "redirect:/auth/cek";
                }
                authService.createAndSendOtp(user);
                model.addAttribute("userId", user.getId());
                model.addAttribute("nama", nama);
                model.addAttribute("angkatan", angkatan);
                return "html/auth/form-otp";
            } else {
                model.addAttribute("error", "Email belum tersedia pada akun");
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
            @RequestParam("nama") String nama,
            @RequestParam("angkatan") Long angkatan,
            RedirectAttributes redirect,
            Model model) {

        Optional<Users> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            redirect.addFlashAttribute("error", "User tidak ditemukan.");
            return "redirect:/auth/form-otp?nama=" + nama + "&angkatan=" + angkatan;
        }

        Users user = userOpt.get();

        Optional<OtpToken> otpTokenOpt = otpTokenRepository.findByOtpAndUser(otp, user);

        if (otpTokenOpt.isEmpty()) {
            redirect.addFlashAttribute("error", "Kode OTP tidak valid.");
            return "redirect:/auth/form-otp?nama=" + nama + "&angkatan=" + angkatan;
        }

        OtpToken otpToken = otpTokenOpt.get();

        if (otpToken.isVerified()) {
            redirect.addFlashAttribute("error", "Kode OTP sudah digunakan.");
            return "redirect:/auth/form-otp?nama=" + nama + "&angkatan=" + angkatan;
        }

        if (otpToken.getExpiredAt().isBefore(LocalDateTime.now())) {
            redirect.addFlashAttribute("error", "Kode OTP telah kedaluwarsa.");
            return "redirect:/auth/form-otp?nama=" + nama + "&angkatan=" + angkatan;
        }

        otpToken.setVerified(true);
        otpTokenRepository.save(otpToken);

        // Jika valid, arahkan ke form-lupa-password sambil bawa nama & angkatan
        return "redirect:/auth/form-lupa-password?nama=" + nama + "&angkatan=" + angkatan;
    }

    @GetMapping("/form-lupa-password")
    public String formLupaPassword(
            @RequestParam(name = "nama", required = false) String nama,
            @RequestParam(name = "angkatan", required = false) Long angkatan,
            Model model) {

        Optional<Users> param = userRepository.findByNamaAndAngkatan(nama, angkatan);

        if (param.isPresent()) {
            Users user = param.get();
            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setNama(user.getNama());
            loginRequest.setAngkatan(user.getAngkatan());
            loginRequest.setEmail(user.getEmail());

            model.addAttribute("loginRequest", loginRequest);
            return "html/auth/lupa-password";
        } else {
            return "redirect:/auth/cek";
        }
    }

    @PostMapping("/proses-lupa-password")
    public String prosesLupaPassword(
            @ModelAttribute LoginRequest loginRequest,
            RedirectAttributes redirect) {

        boolean userValid = authService.cekUserValid(loginRequest.getNama(), loginRequest.getAngkatan(),
                loginRequest.getEmail());

        log.debug("Password reset attempt for user: {}", loginRequest.getEmail());
        if (!userValid) {
            redirect.addFlashAttribute("error", "Data tidak valid atau tidak ditemukan");
            return "redirect:/auth/tampilan-lupa-password?naama=" + loginRequest.getNama()
                    + "&angkatan=" + loginRequest.getAngkatan()
                    + "&email=" + loginRequest.getEmail();
        }

        authService.updateLupaPassword(loginRequest.getEmail(), loginRequest.getPassword());
        redirect.addAttribute("nama", loginRequest.getNama());
        redirect.addAttribute("angkatan", loginRequest.getAngkatan());
        return "redirect:/auth/login";
    }

    @GetMapping("/logout")
    public String logout(
            HttpServletResponse response,
            RedirectAttributes redirect) {
        Cookie cookie = new Cookie("jwt", null);
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        cookie.setPath("/");
        response.addCookie(cookie);

        redirect.addAttribute("message", "Berhasil Keluar");
        redirect.addAttribute("redirectUrl", "/auth/cek");
        return "redirect:/auth/sukses";
    }

}
