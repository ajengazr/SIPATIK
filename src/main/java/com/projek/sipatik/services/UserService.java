package com.projek.sipatik.services;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.projek.sipatik.dto.EditPasswordRequest;
import com.projek.sipatik.exception.FieldValidationException;
import com.projek.sipatik.exception.UnauthorizedException;
import com.projek.sipatik.models.Users;
import com.projek.sipatik.repositories.UserRepository;
import com.projek.sipatik.security.JwtUtil;

/**
 * Urusan akun alumni (identitas dan kata sandi).
 *
 * Seluruh logika pencatatan infak sudah dipindah ke {@link InfakService} supaya
 * aturan minimal nominal, tanggal, dan status hanya punya satu tempat.
 */
@Service
public class UserService {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public Users getUserFromToken(String token) {
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException("Sesi tidak ditemukan. Silakan login ulang.");
        }
        String email = jwtUtil.extractEmail(token);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("User tidak ditemukan"));
    }

    public void updatePassword(Users user, EditPasswordRequest request) {
        if (request.getOldPassword() == null
                || !passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new FieldValidationException("oldPassword", "Password lama salah.");
        }

        if (request.getNewPassword() == null
                || request.getNewPassword().length() < 8
                || request.getNewPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new FieldValidationException(
                    "newPassword", "Password baru minimal 8 karakter dan tidak boleh terlalu panjang.");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new FieldValidationException("confirmPassword", "Konfirmasi password tidak cocok.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }
}
