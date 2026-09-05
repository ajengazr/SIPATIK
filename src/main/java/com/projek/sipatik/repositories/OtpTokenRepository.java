package com.projek.sipatik.repositories;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.projek.sipatik.models.OtpToken;

public interface OtpTokenRepository extends JpaRepository<OtpToken, Long>{
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OtpToken> findTopByUserIdAndVerifiedFalseOrderByIdDesc(Long userId);

    Optional<OtpToken> findTopByUserIdOrderByIdDesc(Long userId);

    List<OtpToken> findAllByUserIdAndVerifiedFalse(Long userId);

    Optional<OtpToken> findByResetTokenHash(String resetTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OtpToken o JOIN FETCH o.user WHERE o.resetTokenHash = :resetTokenHash")
    Optional<OtpToken> findByResetTokenHashForUpdate(@Param("resetTokenHash") String resetTokenHash);
}
