package com.projek.sipatik.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projek.sipatik.dto.UserRequest;
import com.projek.sipatik.models.AlumniInvitation;
import com.projek.sipatik.models.Role;
import com.projek.sipatik.models.Users;
import com.projek.sipatik.repositories.AlumniInvitationRepository;
import com.projek.sipatik.repositories.UserRepository;

@Service
public class AlumniInvitationService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;
    private static final int INVITATION_DAYS = 7;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_BCRYPT_BYTES = 72;
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,63}$", Pattern.CASE_INSENSITIVE);

    private final AlumniInvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AlumniInvitationService(AlumniInvitationRepository invitationRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.invitationRepository = invitationRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Membuat satu undangan terkini. Lock USER menyerialkan dua klik admin paralel. */
    @Transactional
    public IssuedInvitation issue(Long userId, String adminEmail) {
        Users user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("Alumni tidak ditemukan."));
        requireCandidate(user);

        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime now = LocalDateTime.now();

        AlumniInvitation invitation = invitationRepository.findByUserId(user.getId())
                .orElseGet(AlumniInvitation::new);
        invitation.setUser(user);
        invitation.setTokenHash(hash(rawToken));
        invitation.setCreatedAt(now);
        invitation.setExpiresAt(now.plusDays(INVITATION_DAYS));
        invitation.setUsedAt(null);
        invitation.setCreatedBy(adminEmail == null || adminEmail.isBlank() ? "admin" : adminEmail);
        invitationRepository.saveAndFlush(invitation);
        return new IssuedInvitation(rawToken, invitation.getExpiresAt(), user.getId(), user.getNama());
    }

    @Transactional(readOnly = true)
    public Optional<InvitationAccount> findActive(String rawToken) {
        if (!hasValidTokenShape(rawToken)) {
            return Optional.empty();
        }
        return invitationRepository.findByTokenHash(hash(rawToken))
                .filter(this::isActive)
                .filter(invitation -> isCandidate(invitation.getUser()))
                .map(invitation -> new InvitationAccount(
                        invitation.getUser().getId(),
                        invitation.getUser().getNama(),
                        invitation.getUser().getAngkatan()));
    }

    /** Resolve identitas dari token server-side; tidak menerima userId dari browser. */
    @Transactional
    public Users register(String rawToken, UserRequest request, Map<String, String> errors) {
        if (!hasValidTokenShape(rawToken)) {
            throw new IllegalStateException("Undangan tidak valid atau telah berakhir.");
        }
        String tokenHash = hash(rawToken);
        Long userId = invitationRepository.findUserIdByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalStateException("Undangan tidak valid atau telah berakhir."));
        // Semua jalur memakai urutan lock USER lalu invitation. Urutan konsisten ini
        // mencegah deadlock ketika admin membuat ulang undangan saat alumni mendaftar.
        // Query pertama hanya mengambil scalar userId supaya entity lama tidak masuk
        // persistence context sebelum transaksi lain melepaskan lock-nya.
        Users user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException("Undangan tidak valid atau telah berakhir."));
        AlumniInvitation invitation = invitationRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new IllegalStateException("Undangan tidak valid atau telah berakhir."));
        if (!isActive(invitation) || !invitation.getUser().getId().equals(user.getId())) {
            throw new IllegalStateException("Undangan tidak valid atau telah berakhir.");
        }
        requireCandidate(user);
        validate(request, errors);
        if (!errors.isEmpty()) {
            return user;
        }

        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNomorHp(request.getNomorHp());
        user.setJenjang(request.getJenjang().toUpperCase(Locale.ROOT));
        userRepository.saveAndFlush(user);

        invitation.setUsedAt(LocalDateTime.now());
        invitationRepository.save(invitation);
        return user;
    }

    private void validate(UserRequest request, Map<String, String> errors) {
        request.normalizeNomorWa();
        String email = normalizeEmail(request.getEmail());
        request.setEmail(email);
        if (email == null || email.isBlank()) {
            errors.put("email", "Email tidak boleh kosong.");
        } else if (!EMAIL.matcher(email).matches()) {
            errors.put("email", "Format email tidak valid.");
        } else if (userRepository.existsByEmail(email)) {
            errors.put("email", "Email sudah digunakan.");
        }

        String password = request.getPassword();
        if (password == null || password.isBlank()) {
            errors.put("password", "Password tidak boleh kosong.");
        } else if (password.length() < MIN_PASSWORD_LENGTH
                || password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_BYTES) {
            errors.put("password", "Password minimal 8 karakter dan tidak boleh terlalu panjang.");
        }

        String nomorHp = request.getNomorHp();
        if (nomorHp == null || nomorHp.isBlank()) {
            errors.put("nomorHp", "Nomor HP tidak boleh kosong.");
        } else if (!nomorHp.matches("^08[0-9]{9,11}$")) {
            errors.put("nomorHp", "Nomor WA harus diawali 08 dan terdiri dari 11–13 digit.");
        }

        String jenjang = request.getJenjang();
        if (jenjang == null || jenjang.isBlank()) {
            errors.put("jenjang", "Jenjang wajib dipilih.");
        } else if (!"S1".equalsIgnoreCase(jenjang) && !"D3".equalsIgnoreCase(jenjang)) {
            errors.put("jenjang", "Jenjang hanya boleh S1 atau D3.");
        }
    }

    private void requireCandidate(Users user) {
        if (!isCandidate(user)) {
            throw new IllegalStateException("Undangan hanya dapat dibuat untuk akun alumni yang belum aktif.");
        }
    }

    private boolean isCandidate(Users user) {
        return user != null && user.getRole() == Role.USER
                && isBlank(user.getEmail()) && isBlank(user.getPassword()) && isBlank(user.getNomorHp());
    }

    private boolean isActive(AlumniInvitation invitation) {
        return invitation.getUsedAt() == null
                && invitation.getExpiresAt() != null
                && invitation.getExpiresAt().isAfter(LocalDateTime.now());
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean hasValidTokenShape(String rawToken) {
        // Token 32 byte Base64 URL tanpa padding selalu 43 karakter. Menolak bentuk
        // lain lebih awal juga membatasi input arbitrer sebelum hashing/query DB.
        return rawToken != null && rawToken.length() == 43
                && rawToken.matches("^[A-Za-z0-9_-]{43}$");
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 tidak tersedia", e);
        }
    }

    public record IssuedInvitation(String token, LocalDateTime expiresAt, Long userId, String nama) {
    }

    public record InvitationAccount(Long userId, String nama, Long angkatan) {
    }
}
