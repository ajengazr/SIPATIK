package com.projek.sipatik.controllers;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.projek.sipatik.dto.LoginRequest;
import com.projek.sipatik.dto.LoginResponse;
import com.projek.sipatik.models.AdminToken;
import com.projek.sipatik.models.Role;
import com.projek.sipatik.models.Users;
import com.projek.sipatik.repositories.UserRepository;
import com.projek.sipatik.security.JwtUtil;
import com.projek.sipatik.services.AuthService;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/auth-adm")
public class AdmAuthController {
    private static final Logger log = LoggerFactory.getLogger(AdmAuthController.class);

    @Autowired
    private AuthService authService;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private UserRepository userRepository;
    @Value("${app.security.cookie-secure:true}")
    private boolean secureJwtCookie;

    @GetMapping("/login-admin")
    public String login(Model model) {
        model.addAttribute("loginRequest", new LoginRequest());
        return "html/auth/login-admin";
    }

    // proses login -> generate token -> kirim email
    @PostMapping("/proses-login")
    public String loginAdmin(@RequestParam String email,
            @RequestParam String password,
            RedirectAttributes redirect) {
        try {
            log.debug("Admin login attempt for email: {}", email);
            LoginResponse loginResponse = authService.validateLogin(email, password);

            if (!"ADMIN".equalsIgnoreCase(loginResponse.getRole().name())) {
                redirect.addFlashAttribute("error", "Hanya admin yang bisa login di sini!");
                return "redirect:/auth-adm/login-admin";
            }

            // Generate token khusus konfirmasi
            AdminToken token = authService.generateToken(email);

            // Kirim email (pakai token, bukan link)
            authService.sendTokenEmail(email, token.getToken());

            redirect.addFlashAttribute("message", "Token sudah dikirim ke email Anda. Silakan input token.");
            return "redirect:/auth-adm/token-form?email=" + email;

        } catch (Exception e) {
            log.error("Admin login failed for email: {}", email, e);
            redirect.addFlashAttribute("error", "Email atau password salah!");
            return "redirect:/auth-adm/login-admin";
        }
    }

    @GetMapping("/token-form")
    public String tokenForm(@RequestParam(required = false) String email, Model model) {
        model.addAttribute("email", email);
        return "html/auth/token-form"; // ganti sesuai path template-mu
    }

    @PostMapping("/verify-token")
    public String verifyToken(@RequestParam String token,
            RedirectAttributes redirect,
            HttpServletResponse response) {
        Users user = authService.validateToken(token);

        if (user == null || user.getRole() != Role.ADMIN) {
            redirect.addFlashAttribute("error", "Token tidak valid atau sudah kadaluarsa!");
            return "redirect:/auth-adm/token-form";
        }

        // generate JWT
        String jwt = jwtUtil.generateToken(user);
        addJwtCookie(response, jwt);

        return "redirect:/admin/dash-admin";
    }

    @PostMapping("/resend-token")
    public ResponseEntity<Map<String, String>> resendToken(@RequestParam String email) {
        String message = "Jika akun admin valid, token baru akan dikirim sesuai batas waktu pengiriman.";
        try {
            userRepository.findByEmail(email)
                    .filter(user -> user.getRole() == Role.ADMIN)
                    .ifPresent(user -> authService.resendToken(email, user));
        } catch (RuntimeException e) {
            // Respons sengaja identik untuk akun tidak ada, role salah, cooldown,
            // maupun pengiriman diterima agar endpoint publik tidak menjadi oracle.
            log.debug("Admin token resend was not performed");
        }
        return ResponseEntity.ok(Map.of("status", "accepted", "message", message));
    }

    private void addJwtCookie(HttpServletResponse response, String value) {
        ResponseCookie cookie = ResponseCookie.from("jwt", value)
                .httpOnly(true)
                .secure(secureJwtCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(3600)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
