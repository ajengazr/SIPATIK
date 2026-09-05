package com.projek.sipatik.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import com.projek.sipatik.dto.LoginResponse;
import com.projek.sipatik.models.AdminToken;
import com.projek.sipatik.models.OtpToken;
import com.projek.sipatik.models.Role;
import com.projek.sipatik.models.Users;
import com.projek.sipatik.repositories.AdminTokenRepository;
import com.projek.sipatik.repositories.OtpTokenRepository;
import com.projek.sipatik.repositories.UserRepository;
import com.projek.sipatik.security.JwtUtil;
import com.projek.sipatik.exception.FieldValidationException;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int RESET_TOKEN_BYTES = 32;
    private static final int RESET_TOKEN_TTL_MINUTES = 10;
    private static final int OTP_TTL_MINUTES = 5;
    private static final int OTP_RESEND_COOLDOWN_SECONDS = 60;
    private static final int OTP_MAX_FAILED_ATTEMPTS = 5;
    private static final int ADMIN_RESEND_COOLDOWN_SECONDS = 60;
    private static final int MIN_NEW_PASSWORD_LENGTH = 8;
    private static final int MAX_BCRYPT_PASSWORD_BYTES = 72;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JavaMailSender javaMailSender;
    @Autowired
    private OtpTokenRepository otpTokenRepository;
    @Autowired
    private SpringTemplateEngine templateEngine;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private AdminTokenRepository adminTokenRepository;

    private final Map<String, String> tokenStorage = new HashMap<>();
    private final JavaMailSender mailSender;

    public AuthService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // Generate token baru
    public AdminToken generateToken(String email) {
        email = normalizeEmail(email);
        Users user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User tidak ditemukan"));

        // kalau ada token lama belum expired → hapus semua dulu
        List<AdminToken> existingTokens = adminTokenRepository.findAllByEmailAndUsedFalse(email);
        if (!existingTokens.isEmpty()) {
            adminTokenRepository.deleteAll(existingTokens);
        }

        AdminToken token = new AdminToken();
        token.setEmail(email);
        token.setToken(UUID.randomUUID().toString()); // kode random
        token.setCreatedAt(LocalDateTime.now());
        token.setExpiresAt(LocalDateTime.now().plusMinutes(5)); // expired 5 menit
        token.setUsed(false);
        token.setUser(user);

        return adminTokenRepository.save(token);
    }

    // Validasi token dan return Users jika valid, null jika invalid
    @Transactional
    public Users validateToken(String tokenInput) {
        Optional<AdminToken> tokenOpt = adminTokenRepository.findByToken(tokenInput);
        if (tokenOpt.isEmpty())
            return null;

        AdminToken token = tokenOpt.get();
        if (token.isUsed() || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            return null;
        }

        // tandai token sudah digunakan
        token.setUsed(true);
        adminTokenRepository.save(token);
        return token.getUser();
    }

    @Transactional
    public void resendToken(String email, Users user) {
        email = normalizeEmail(email);
        LocalDateTime now = LocalDateTime.now();

        // ambil token terakhir
        AdminToken lastToken = adminTokenRepository.findTopByEmailOrderByCreatedAtDesc(email).orElse(null);

        if (lastToken != null) {
            LocalDateTime lastSentAt = lastToken.getLastResendTime() != null
                    ? lastToken.getLastResendTime()
                    : lastToken.getCreatedAt();
            LocalDateTime bolehKirimLagi = lastSentAt == null
                    ? now
                    : lastSentAt.plusSeconds(ADMIN_RESEND_COOLDOWN_SECONDS);
            if (now.isBefore(bolehKirimLagi)) {
                long sisaDetik = Math.max(1, Duration.between(now, bolehKirimLagi).toSeconds() + 1);
                throw new IllegalStateException(
                        "Tunggu " + sisaDetik + " detik sebelum meminta token baru.");
            }
        }

        // Cabut seluruh token login lama agar hanya token terbaru yang dapat dipakai.
        List<AdminToken> activeTokens = adminTokenRepository.findAllByEmailAndUsedFalse(email);
        activeTokens.forEach(activeToken -> activeToken.setUsed(true));
        if (!activeTokens.isEmpty()) {
            adminTokenRepository.saveAll(activeTokens);
        }

        String newToken = UUID.randomUUID().toString();

        AdminToken token = new AdminToken();
        token.setEmail(email);
        token.setToken(newToken);
        token.setCreatedAt(now);
        token.setExpiresAt(now.plusMinutes(5));
        token.setUsed(false);
        int resendSebelumnya = lastToken == null || lastToken.getResendCount() == null
                ? 0
                : lastToken.getResendCount();
        token.setResendCount(resendSebelumnya + 1);
        token.setLastResendTime(now);
        token.setUser(user);

        adminTokenRepository.save(token);

        // kirim email
        sendTokenEmail(email, newToken);
    }

    public String getEmailByToken(String token) {
        return tokenStorage.get(token);
    }

    public void sendTokenEmail(String email, String token) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setTo(email);
            helper.setSubject("Konfirmasi Login Anda");

            String htmlContent = """
                    <div style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; border-radius: 10px; overflow: hidden; box-shadow: 0 4px 10px rgba(0,0,0,0.1);">
                        <div style="background: linear-gradient(90deg, #7b5cff, #8f6bff); padding: 20px; text-align: center; color: white; font-size: 20px; font-weight: bold;">
                            Konfirmasi Login Anda
                        </div>
                        <div style="padding: 30px; text-align: center; color: #333;">
                            <p style="font-size: 16px;">Terima kasih telah menggunakan layanan kami. Gunakan kode TOKEN berikut untuk menyelesaikan verifikasi:</p>
                            <div style="font-size: 32px; font-weight: bold; margin: 20px 0; background: #f5f5f5; display: inline-block; padding: 15px 25px; border-radius: 8px;">
                                %s
                            </div>
                            <p style="font-size: 14px; color: #666;">Kode ini berlaku selama <b>5 menit</b>. Jangan bagikan kode ini kepada siapa pun.</p>
                        </div>
                        <div style="background: #f9f9f9; padding: 15px; font-size: 12px; color: #777; text-align: center;">
                            Jika Anda tidak meminta kode ini, abaikan email ini atau hubungi <a href="mailto:ajengazzahara04@gmail.com" style="color: #4f46e5; text-decoration: none;">dukungan kami</a>.<br>
                            © 2025 Pemberdayaan Umat Berkelanjutan. Semua hak dilindungi.
                        </div>
                    </div>
                    """
                    .formatted(token);

            helper.setText(htmlContent, true); // true = enable HTML
            mailSender.send(mimeMessage);

        } catch (MessagingException e) {
            log.error("Failed to send admin login token email", e);
        }
    }

    public String generateOtp() {
        // nextInt(900000) menghasilkan 0..899999, sehingga hasil akhir selalu
        // tepat enam digit dalam rentang 100000..999999.
        int otp = 100000 + SECURE_RANDOM.nextInt(900000);
        return Integer.toString(otp);
    }

    public void sendOtpEmail(String toEmail, String otp, String nama) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // Menyiapkan data untuk template
            Context context = new Context();
            context.setVariable("otp", otp);
            context.setVariable("nama", nama);

            // nge-generate isi email dari html
            String htmlContent = templateEngine.process("html/auth/email-otp", context);

            helper.setTo(toEmail);
            helper.setSubject("Kode OTP Reset Password SIPATIK");
            helper.setText(htmlContent, true);

            javaMailSender.send(message);

        } catch (MessagingException e) {
            log.error("Failed to send password reset email", e);
            throw new RuntimeException("Gagal mengirim email", e);
        }
    }

    @Transactional
    public void createAndSendOtp(Users requestedUser) {
        if (requestedUser == null || requestedUser.getId() == null) {
            throw new IllegalStateException("Akun tidak valid.");
        }

        Users user = userRepository.findByIdForUpdate(requestedUser.getId())
                .orElseThrow(() -> new IllegalStateException("Akun tidak valid."));
        if (user.getRole() != Role.USER || user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalStateException("Akun tidak valid.");
        }

        LocalDateTime now = LocalDateTime.now();
        OtpToken latest = otpTokenRepository.findTopByUserIdOrderByIdDesc(user.getId()).orElse(null);
        if (latest != null) {
            LocalDateTime sentAt = latest.getCreatedAt();
            if (sentAt == null && latest.getExpiredAt() != null) {
                sentAt = latest.getExpiredAt().minusMinutes(OTP_TTL_MINUTES);
            }
            if (sentAt != null && now.isBefore(sentAt.plusSeconds(OTP_RESEND_COOLDOWN_SECONDS))) {
                long remaining = Math.max(1,
                        Duration.between(now, sentAt.plusSeconds(OTP_RESEND_COOLDOWN_SECONDS)).toSeconds() + 1);
                throw new IllegalStateException("Tunggu " + remaining + " detik sebelum meminta kode baru.");
            }
        }

        List<OtpToken> activeTokens = otpTokenRepository.findAllByUserIdAndVerifiedFalse(user.getId());
        activeTokens.forEach(token -> token.setVerified(true));
        if (!activeTokens.isEmpty()) {
            otpTokenRepository.saveAll(activeTokens);
        }

        String otp = generateOtp();

        OtpToken otpToken = new OtpToken();
        otpToken.setOtp(otp);
        otpToken.setUser(user);
        otpToken.setEmail(user.getEmail());
        otpToken.setCreatedAt(now);
        otpToken.setExpiredAt(now.plusMinutes(OTP_TTL_MINUTES));
        otpToken.setFailedAttempts(0);

        otpTokenRepository.save(otpToken);
        sendOtpEmail(user.getEmail(), otp, user.getNama());
    }

    /**
     * Memverifikasi OTP di dalam lock database lalu menerbitkan grant reset yang
     * terikat ke satu akun. Hanya hash grant disimpan; token mentah diberikan ke
     * controller untuk cookie HttpOnly.
     */
    @Transactional(noRollbackFor = OtpVerificationException.class)
    public PasswordResetGrant verifyOtpAndIssuePasswordReset(Long userId, String otp) {
        if (userId == null || otp == null || otp.isBlank()) {
            throw new IllegalStateException("Kode OTP tidak valid atau kedaluwarsa.");
        }

        OtpToken otpToken = otpTokenRepository.findTopByUserIdAndVerifiedFalseOrderByIdDesc(userId)
                .orElseThrow(() -> new IllegalStateException("Kode OTP tidak valid atau kedaluwarsa."));
        LocalDateTime now = LocalDateTime.now();
        int failedAttempts = otpToken.getFailedAttempts() == null ? 0 : otpToken.getFailedAttempts();
        if (otpToken.isVerified()
                || otpToken.getExpiredAt() == null
                || !otpToken.getExpiredAt().isAfter(now)
                || otpToken.getOtp() == null
                || otpToken.getUser() == null
                || otpToken.getUser().getRole() != Role.USER
                || failedAttempts >= OTP_MAX_FAILED_ATTEMPTS) {
            throw new IllegalStateException("Kode OTP tidak valid atau kedaluwarsa.");
        }

        boolean otpMatches = MessageDigest.isEqual(
                otpToken.getOtp().getBytes(StandardCharsets.UTF_8),
                otp.trim().getBytes(StandardCharsets.UTF_8));
        if (!otpMatches) {
            failedAttempts++;
            otpToken.setFailedAttempts(failedAttempts);
            if (failedAttempts >= OTP_MAX_FAILED_ATTEMPTS) {
                otpToken.setVerified(true);
            }
            otpTokenRepository.save(otpToken);
            // Exception khusus ini tidak me-rollback transaksi, sehingga penghitung
            // percobaan salah benar-benar tersimpan sebelum respons ditolak.
            throw new OtpVerificationException();
        }

        byte[] tokenBytes = new byte[RESET_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        otpToken.setVerified(true);
        otpToken.setResetTokenHash(hashResetToken(rawToken));
        otpToken.setResetExpiresAt(now.plusMinutes(RESET_TOKEN_TTL_MINUTES));
        otpToken.setResetUsed(false);
        otpTokenRepository.save(otpToken);

        Users user = otpToken.getUser();
        return new PasswordResetGrant(rawToken, user.getNama(), user.getAngkatan(), user.getEmail());
    }

    public Optional<PasswordResetAccount> findActivePasswordReset(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        return otpTokenRepository.findByResetTokenHash(hashResetToken(rawToken))
                .filter(this::isActivePasswordReset)
                .map(token -> {
                    Users user = token.getUser();
                    return new PasswordResetAccount(user.getNama(), user.getAngkatan(), user.getEmail());
                });
    }

    /**
     * Lock pesimistis membuat pemeriksaan, perubahan password, dan penandaan token
     * sebagai terpakai menjadi satu transaksi. Dua POST paralel tidak dapat memakai
     * grant yang sama.
     */
    @Transactional
    public PasswordResetAccount consumePasswordReset(String rawToken, String newPassword) {
        validateNewPassword(newPassword);
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalStateException("Sesi reset password tidak valid atau telah berakhir.");
        }

        OtpToken resetToken = otpTokenRepository
                .findByResetTokenHashForUpdate(hashResetToken(rawToken))
                .orElseThrow(() -> new IllegalStateException(
                        "Sesi reset password tidak valid atau telah berakhir."));
        if (!isActivePasswordReset(resetToken)) {
            throw new IllegalStateException("Sesi reset password tidak valid atau telah berakhir.");
        }

        Users user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        resetToken.setResetUsed(true);
        userRepository.save(user);
        otpTokenRepository.save(resetToken);

        return new PasswordResetAccount(user.getNama(), user.getAngkatan(), user.getEmail());
    }

    private boolean isActivePasswordReset(OtpToken token) {
        return token.isVerified()
                && !Boolean.TRUE.equals(token.getResetUsed())
                && token.getResetExpiresAt() != null
                && token.getResetExpiresAt().isAfter(LocalDateTime.now())
                && token.getUser() != null
                && token.getUser().getRole() == Role.USER;
    }

    private void validateNewPassword(String password) {
        if (password == null || password.isBlank()
                || password.length() < MIN_NEW_PASSWORD_LENGTH
                || password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_PASSWORD_BYTES) {
            throw new IllegalArgumentException(
                    "Password minimal 8 karakter dan tidak boleh terlalu panjang.");
        }
    }

    private String hashResetToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 tidak tersedia", e);
        }
    }

    public record PasswordResetGrant(String token, String nama, Long angkatan, String email) {
    }

    public record PasswordResetAccount(String nama, Long angkatan, String email) {
    }

    private static final class OtpVerificationException extends IllegalStateException {
        private OtpVerificationException() {
            super("Kode OTP tidak valid atau kedaluwarsa.");
        }
    }

    public LoginResponse validateLogin(String email, String password) {
        email = normalizeEmail(email);
        // validasi email
        if (email == null || email.isBlank()) {
            throw new FieldValidationException("email", "Email tidak boleh kosong.");
        }
        if (!email.matches("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$")) {
            throw new FieldValidationException("email", "Format email tidak valid.");
        }

        // validasi password
        if (password == null || password.isBlank()) {
            throw new FieldValidationException("password", "Password tidak boleh kosong.");
        }
        // Akun lama mungkin masih memiliki password enam karakter. Saat login kita
        // hanya membatasi panjang aman BCrypt; kebijakan minimal delapan karakter
        // diterapkan ketika membuat atau mengganti password.
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_PASSWORD_BYTES) {
            throw new FieldValidationException("password", "Password terlalu panjang.");
        }
        // Remove strict password pattern validation that was causing admin login to fail
        // The original pattern ^[A-Z][A-Za-z0-9]*$ was too restrictive

        // === Validasi ke DB ===
        Optional<Users> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isEmpty()) {
            throw new FieldValidationException("email", "Email tidak ditemukan.");
        }

        Users user = optionalUser.get();
        log.debug("Validating password for user: {}", email);
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new FieldValidationException("password", "Password atau email salah.");
        }

        // === Buat token ===
        String token = jwtUtil.generateToken(user);

        return new LoginResponse(token, user.getEmail(), user.getNama(), user.getAngkatan(), user.getRole());
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

}
