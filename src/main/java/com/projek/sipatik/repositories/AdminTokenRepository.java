package com.projek.sipatik.repositories;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.projek.sipatik.models.AdminToken;

public interface AdminTokenRepository extends JpaRepository<AdminToken, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AdminToken> findByToken(String token);
    Optional<AdminToken> findByEmailAndUsedFalse(String email);
    List<AdminToken> findAllByEmailAndUsedFalse(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AdminToken> findTopByEmailOrderByCreatedAtDesc(String email);
}
