package com.projek.sipatik.controllers;

import com.projek.sipatik.dto.SetorInfakRequest;
import com.projek.sipatik.exception.BadRequestException;
import com.projek.sipatik.exception.ResourceNotFoundException;
import com.projek.sipatik.models.SetorInfak;
import com.projek.sipatik.models.Users;
import com.projek.sipatik.repositories.SetorInfakRepository;
import com.projek.sipatik.repositories.UserRepository;
import com.projek.sipatik.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/setor-infak")
public class SetorInfakApiController {

    @Autowired
    private SetorInfakRepository setorInfakRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping
    public ResponseEntity<Map<String, Object>> setorInfak(
            @Valid @ModelAttribute SetorInfakRequest request,
            HttpServletRequest httpRequest) {
        
        // Validasi token JWT
        String token = extractTokenFromRequest(httpRequest);
        if (token == null || !jwtUtil.validateToken(token)) {
            throw new BadRequestException("Token tidak valid atau tidak ditemukan");
        }

        // Validasi user
        String userEmail = jwtUtil.extractEmail(token);
        Optional<Users> userOpt = userRepository.findByEmail(userEmail);
        if (userOpt.isEmpty()) {
            throw new ResourceNotFoundException("User tidak ditemukan");
        }

        Users user = userOpt.get();

        // Validasi data
        if (request.getNominal() == null || request.getNominal() <= 0) {
            throw new BadRequestException("Nominal infak harus lebih dari 0");
        }

        if (request.getBank() == null || request.getBank().trim().isEmpty()) {
            throw new BadRequestException("Bank tidak boleh kosong");
        }

        if (request.getBuktiTransfer() == null || request.getBuktiTransfer().isEmpty()) {
            throw new BadRequestException("Bukti transfer tidak boleh kosong");
        }

        try {
            // Simpan file bukti transfer
            String fileName = saveFile(request.getBuktiTransfer());
            
            // Simpan data
            SetorInfak setorInfak = SetorInfak.builder()
                    .user(user)
                    .bank(request.getBank())
                    .nominal(request.getNominal())
                    .buktiTransfer(fileName)
                    .dikonfirmasi(false)
                    .tanggalInfak(LocalDate.now())
                    .build();
            
            SetorInfak savedInfak = setorInfakRepository.save(setorInfak);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Infak berhasil disetor");
            response.put("data", savedInfak);

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            throw new BadRequestException("Gagal menyimpan file bukti transfer: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getSetorInfak(@PathVariable Long id) {
        Optional<SetorInfak> infakOpt = setorInfakRepository.findById(id);
        if (infakOpt.isEmpty()) {
            throw new ResourceNotFoundException("Data infak tidak ditemukan");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", infakOpt.get());

        return ResponseEntity.ok(response);
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private String saveFile(MultipartFile file) throws IOException {
        // Buat direktori uploads jika belum ada
        Path uploadDir = Paths.get("uploads");
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }

        // Generate nama file unik
        String originalFileName = file.getOriginalFilename();
        String fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
        String fileName = UUID.randomUUID().toString() + "_" + originalFileName;
        
        // Simpan file
        Path filePath = uploadDir.resolve(fileName);
        Files.copy(file.getInputStream(), filePath);
        
        return fileName;
    }
}
