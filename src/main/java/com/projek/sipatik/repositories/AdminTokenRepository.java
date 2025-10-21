package com.projek.sipatik.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projek.sipatik.models.AdminToken;

public interface AdminTokenRepository extends JpaRepository<AdminToken, Long> {
    Optional<AdminToken> findByToken(String token);
    Optional<AdminToken> findByEmailAndUsedFalse(String email);
    List<AdminToken> findAllByEmailAndUsedFalse(String email);
    Optional<AdminToken> findTopByEmailOrderByCreatedAtDesc(String email);
}
