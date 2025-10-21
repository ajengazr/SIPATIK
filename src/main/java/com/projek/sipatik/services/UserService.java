package com.projek.sipatik.services;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.projek.sipatik.dto.EditPasswordRequest;
import com.projek.sipatik.dto.SetorInfakRequest;
import com.projek.sipatik.exception.FieldValidationException;
import com.projek.sipatik.models.SetorInfak;
import com.projek.sipatik.models.Users;
import com.projek.sipatik.repositories.UserRepository;
import com.projek.sipatik.repositories.SetorInfakRepository;
import com.projek.sipatik.security.JwtUtil;

@Service
public class UserService {
    @Autowired
    private SetorInfakRepository setorInfakRespository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public Users getUserFromToken(String token) {
        String email = jwtUtil.extractEmail(token);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User tidak ditemukan"));
    }

    public void simpanInfak(SetorInfakRequest request, Users user) throws IOException {

        validateInfakNominal(user, request.getNominal());

        MultipartFile bukti = request.getBuktiTransfer();
        if (bukti == null || bukti.isEmpty()) {
            throw new FieldValidationException("buktiTransfer", "File bukti transfer wajib diunggah.");
        }

        String originalFilename = bukti.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            originalFilename = "file-bukti";
        }
        originalFilename = StringUtils.cleanPath(originalFilename);
        if (originalFilename.contains("..")) {
            throw new FieldValidationException("buktiTransfer", "Nama file tidak valid.");
        }

        Path uploadDir = Paths.get("uploads").toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);

        // buat nama file unik
        String namaFile = UUID.randomUUID() + "_" + originalFilename;
        Path target = uploadDir.resolve(namaFile);

        try (InputStream in = bukti.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }

        // simpan entitas
        SetorInfak infak = new SetorInfak();
        infak.setUser(user);
        infak.setBank(request.getBank());
        infak.setNominal(request.getNominal());
        infak.setBuktiTransfer(namaFile);
        infak.setTanggalInfak(LocalDate.now());

        setorInfakRespository.save(infak);
    }

    public void validateInfakNominal(Users user, Long nominal) {
        if (nominal == null || nominal <= 0) {
            throw new FieldValidationException("nominal", "Nominal harus lebih dari 0.");
        }

        long minimalInfak = getMinimalInfak(user);

        if (nominal < minimalInfak) {
            throw new FieldValidationException("nominal",
                    "Minimal infak untuk angkatan " + user.getAngkatan() + " jenjang " + user.getJenjang() +" adalah Rp "
                            + String.format("%,d", minimalInfak).replace(',', '.'));
        }
    }

    private long getMinimalInfak(Users user) {
        int angkatan = user.getAngkatan().intValue();
        String jenjang = user.getJenjang();

        switch (angkatan) {
            case 1:
                return 200_000;
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
                return 150_000;
            case 9:
            case 10:
            case 11:
            case 12:
                return 250_000;
            case 13:
                return 400_000;
            case 14:
                return 500_000;
            case 15:
                return 550_000;
            case 16:
                return "S1".equalsIgnoreCase(jenjang) ? 400_000 : 550_000;
            case 17:
                return "S1".equalsIgnoreCase(jenjang) ? 600_000 : 750_000;
            case 18:
                return "S1".equalsIgnoreCase(jenjang) ? 600_000 : 750_000;
            case 19:
                return "S1".equalsIgnoreCase(jenjang) ? 900_000 : 750_000;
            case 20:
                return 800_000;
            case 21:
                return "S1".equalsIgnoreCase(jenjang) ? 950_000 : 850_000;
            case 22:
                return "S1".equalsIgnoreCase(jenjang) ? 950_000 : 850_000;
            case 23:
                return "S1".equalsIgnoreCase(jenjang) ? 1_000_000 : 900_000;
            case 24:
                return "S1".equalsIgnoreCase(jenjang) ? 1_050_000 : 950_000;
            default:
                throw new FieldValidationException("angkatan", "Angkatan tidak dikenali.");
        }
    }

    public void konfirmasiSetoran(Long id) {
        SetorInfak infak = setorInfakRespository.findById(id).orElseThrow();
        infak.setDikonfirmasi(true);
        setorInfakRespository.save(infak);
    }

    public List<SetorInfak> getRekapInfak(Users user) {
        return setorInfakRespository.findByUserAndDikonfirmasiTrue(user);
    }

    public void updatePassword(Users user, EditPasswordRequest request) {
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new FieldValidationException("oldPassword", "Password lama salah.");
        }

        if (request.getNewPassword() == null || request.getNewPassword().length() < 6) {
            throw new FieldValidationException("newPassword", "Password baru minimal 6 karakter.");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new FieldValidationException("confirmPassword", "Konfirmasi password tidak cocok.");
        }

        // update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

}
