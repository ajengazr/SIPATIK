package com.projek.sipatik.services;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import com.projek.sipatik.dto.LoginResponse;
import com.projek.sipatik.dto.UserRequest;
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

    public void resendToken(String email, Users user) {
        LocalDateTime now = LocalDateTime.now();

        // ambil token terakhir
        AdminToken lastToken = adminTokenRepository.findTopByEmailOrderByCreatedAtDesc(email).orElse(null);

        // if (lastToken != null) {
        //     // reset counter kalau sudah lewat 5 menit
        //     if (lastToken.getLastResendTime() == null || lastToken.getLastResendTime().plusMinutes(5).isBefore(now)) {
        //         lastToken.setResendCount(0);
        //     }

        //     // // kalau sudah 3x dalam 5 menit → tolak
        //     // if (lastToken.getResendCount() >= 3) {
        //     //     redirect.addFlashAttribute("cooldown", true);
        //     //     redirect.addFlashAttribute("remaining",
        //     //             Duration.between(now, lastToken.getLastResendTime().plusMinutes(5)).getSeconds());
        //     //     return "redirect:/auth-adm/token-form?email=" + email;
        //     // }
        // }

        // generate token baru (contoh pakai JWT atau UUID)
        String newToken = jwtUtil.generateToken(user);

        AdminToken token = new AdminToken();
        token.setEmail(email);
        token.setToken(newToken);
        token.setCreatedAt(now);
        token.setExpiresAt(now.plusMinutes(5));
        token.setUsed(false);
        token.setResendCount((lastToken == null ? 0 : lastToken.getResendCount()) + 1);
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
            log.error("Failed to send token email to: {}", email, e);
        }
    }

    public boolean cekUserValid(String nama, Long angkatan, String email) {
        return userRepository.findByNamaAndAngkatanAndEmail(nama, angkatan, email).isPresent();
    }

    public void updateLupaPassword(String email, String newPassword) {
        Optional<Users> optionalUser = userRepository.findByEmail(email);

        if (optionalUser.isPresent()) {
            Users user = optionalUser.get();

            String hashedPassword = passwordEncoder.encode(newPassword);
            user.setPassword(hashedPassword);

            userRepository.save(user);
        }
    }

    public String generateOtp() {
        Random random = new Random();
        Long otp = 100000 + random.nextLong(999999);
        return String.valueOf(otp);
    }

    public void sendOtpEmail(String toEmail, String otp, String nama) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // Menyiapkan data untuk template
            Context context = new Context();
            context.setVariable("otp", otp);
            context.setVariable("nama", nama);
            log.debug("Generated OTP for {}: {}", toEmail, otp);

            // nge-generate isi email dari html
            String htmlContent = templateEngine.process("html/auth/email-otp", context);

            helper.setTo(toEmail);
            helper.setSubject("Kode OTP Reset Password SIPATIK");
            helper.setText(htmlContent, true);

            javaMailSender.send(message);

        } catch (MessagingException e) {
            log.error("Failed to send OTP email to: {}", toEmail, e);
            throw new RuntimeException("Gagal mengirim email", e);
        }
    }

    public void createAndSendOtp(Users user) {
        String otp = generateOtp();

        OtpToken otpToken = new OtpToken();
        otpToken.setOtp(otp);
        otpToken.setUser(user);
        otpToken.setEmail(user.getEmail());
        otpToken.setExpiredAt(LocalDateTime.now().plusMinutes(5));

        otpTokenRepository.save(otpToken);
        sendOtpEmail(user.getEmail(), otp, user.getNama());
    }

    public void validasiDaftar(Long id, UserRequest request, Map<String, String> errors) {
        Users user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User tidak ditemukan."));
        // if(user == null){
        // return "html/not-found";
        // }

        // validasi nomor
        request.normalizeNomorWa();

        // validasi email
        String email = request.getEmail();
        if (email == null || email.isBlank()) {
            errors.put("email", "Email tidak boleh kosong.");
        } else if (!email.matches("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$")) {
            errors.put("email", "Format email tidak valid");
        } else if (!email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
            errors.put("email", "Email sudah digunakan");
        }

        // validasi pass
        String password = request.getPassword();
        if (password == null || password.isBlank()) {
            errors.put("password", "Password tidak boleh kosong.");
        } else if (password.length() < 6 || password.length() > 8) {
            errors.put("password", "Password harus 6-8 karakter");
        } else if (!password.matches("^[A-Z][A-Za-z0-9]*$")) {
            errors.put("password", "Password harus diawali huruf kapital dan berisi huruf/angka");
        }

        // validasi nomor
        String nomorHp = request.getNomorHp();
        if (nomorHp == null || nomorHp.isBlank()) {
            errors.put("nomorHp", "Nomor HP tidak boleh kosong");
        } else if (!nomorHp.matches("^08[0-9]{9,11}$")) {
            errors.put("nomorHp", "Nomor WA harus diawali 08 dan 11-13 digit");
        }

        // validasi jenjang
        String jenjang = request.getJenjang();
        if (jenjang == null || jenjang.isBlank()) {
            errors.put("jenjang", "Jenjang wajib dipilih.");
        } else if (!jenjang.equalsIgnoreCase("S1") && !jenjang.equalsIgnoreCase("D3")) {
            errors.put("jenjang", "Jenjang hanya boleh S1 atau D3.");
        }

        // Jika tidak ada error, simpan
        if (errors.isEmpty()) {
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode(password));
            user.setNomorHp(nomorHp);
            user.setJenjang(jenjang);
            if (user.getRole() == null) {
                user.setRole(Role.USER);
            }
            userRepository.save(user);
        }

    }

    public LoginResponse validateLogin(String email, String password) {
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
        if (password.length() < 6 || password.length() > 8) {
            throw new FieldValidationException("password", "Password harus 6-8 karakter.");
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

}