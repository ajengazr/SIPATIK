package com.projek.sipatik.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.projek.sipatik.dto.LoginRequest;
import com.projek.sipatik.models.OtpToken;
import com.projek.sipatik.models.Role;
import com.projek.sipatik.models.Users;
import com.projek.sipatik.repositories.OtpTokenRepository;
import com.projek.sipatik.repositories.UserRepository;
import com.projek.sipatik.services.AuthService;
import com.projek.sipatik.services.AuthService.PasswordResetGrant;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetFlowTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OtpTokenRepository otpTokenRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long userId;
    private Long otpTokenId;

    @AfterEach
    void cleanup() {
        if (otpTokenId != null && otpTokenRepository.existsById(otpTokenId)) {
            otpTokenRepository.deleteById(otpTokenId);
        }
        if (userId != null && userRepository.existsById(userId)) {
            userRepository.deleteById(userId);
        }
    }

    @Test
    void postResetTanpaTokenDitolak() throws Exception {
        mockMvc.perform(post("/auth/proses-lupa-password")
                        .with(csrf())
                        .param("password", "Baru1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/cek"))
                .andExpect(flash().attribute(
                        "error", "Sesi reset password tidak valid atau telah berakhir."));
    }

    @Test
    void formResetTanpaTokenDitolak() throws Exception {
        mockMvc.perform(get("/auth/form-lupa-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/cek"));
    }

    @Test
    void pengirimanOtpTidakBolehDipicuDenganGet() throws Exception {
        mockMvc.perform(get("/auth/form-otp")
                        .param("nama", "Siapa Saja")
                        .param("angkatan", "20"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void tokenResetTerikatKeAkunDanHanyaDapatDipakaiSekali() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String nama = "ResetTester" + suffix;
        Users user = new Users();
        user.setNama(nama);
        user.setEmail("reset-" + suffix + "@example.org");
        user.setPassword(passwordEncoder.encode("Lama123"));
        user.setAngkatan(20L);
        user.setRole(Role.USER);
        user = userRepository.save(user);
        userId = user.getId();

        OtpToken otpToken = new OtpToken();
        otpToken.setOtp("654321");
        otpToken.setEmail(user.getEmail());
        otpToken.setExpiredAt(LocalDateTime.now().plusMinutes(5));
        otpToken.setVerified(false);
        otpToken.setUser(user);
        otpToken = otpTokenRepository.save(otpToken);
        otpTokenId = otpToken.getId();

        PasswordResetGrant grant = authService.verifyOtpAndIssuePasswordReset(userId, "654321");
        Cookie resetCookie = new Cookie("sipatik_reset", grant.token());

        mockMvc.perform(get("/auth/form-lupa-password").cookie(resetCookie))
                .andExpect(status().isOk())
                .andExpect(view().name("html/auth/lupa-password"))
                .andExpect(model().attribute("loginRequest", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.hasProperty("nama", org.hamcrest.Matchers.is(nama)),
                        org.hamcrest.Matchers.hasProperty("email", org.hamcrest.Matchers.is(user.getEmail())))));

        mockMvc.perform(post("/auth/proses-lupa-password")
                        .with(csrf())
                        .cookie(resetCookie)
                        // Identitas palsu sengaja dikirim untuk membuktikan controller
                        // hanya memakai akun yang terikat pada token server-side.
                        .param("nama", "KorbanLain")
                        .param("email", "korban@example.org")
                        .param("angkatan", "99")
                        .param("password", "Baru1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/login?nama=" + nama + "&angkatan=20"));

        Users changed = userRepository.findById(userId).orElseThrow();
        assertThat(passwordEncoder.matches("Baru1234", changed.getPassword())).isTrue();

        mockMvc.perform(post("/auth/proses-lupa-password")
                        .with(csrf())
                        .cookie(resetCookie)
                        .param("password", "Lagi1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/cek"));

        Users unchanged = userRepository.findById(userId).orElseThrow();
        assertThat(passwordEncoder.matches("Baru1234", unchanged.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("Lagi1234", unchanged.getPassword())).isFalse();
    }

    @Test
    void otpSelaluEnamDigit() {
        for (int i = 0; i < 2_000; i++) {
            String otp = authService.generateOtp();
            assertThat(otp).matches("[0-9]{6}");
            assertThat(Integer.parseInt(otp)).isBetween(100000, 999999);
        }
    }

    @Test
    void limaOtpSalahTersimpanDanMengunciTokenWalauServiceMelemparException() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Users user = new Users();
        user.setNama("AttemptTester" + suffix);
        user.setEmail("attempt-" + suffix + "@example.org");
        user.setPassword(passwordEncoder.encode("Password123"));
        user.setAngkatan(20L);
        user.setRole(Role.USER);
        user = userRepository.save(user);
        userId = user.getId();

        OtpToken otpToken = new OtpToken();
        otpToken.setOtp("654321");
        otpToken.setEmail(user.getEmail());
        otpToken.setCreatedAt(LocalDateTime.now());
        otpToken.setExpiredAt(LocalDateTime.now().plusMinutes(5));
        otpToken.setFailedAttempts(0);
        otpToken.setVerified(false);
        otpToken.setUser(user);
        otpToken = otpTokenRepository.save(otpToken);
        otpTokenId = otpToken.getId();

        for (int i = 0; i < 5; i++) {
            assertThrows(IllegalStateException.class,
                    () -> authService.verifyOtpAndIssuePasswordReset(userId, "000000"));
        }

        OtpToken locked = otpTokenRepository.findById(otpTokenId).orElseThrow();
        assertThat(locked.getFailedAttempts()).isEqualTo(5);
        assertThat(locked.isVerified()).isTrue();
        assertThrows(IllegalStateException.class,
                () -> authService.verifyOtpAndIssuePasswordReset(userId, "654321"));
    }
}
