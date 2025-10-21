package com.projek.sipatik.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projek.sipatik.models.OtpToken;
import com.projek.sipatik.models.Users;

public interface OtpTokenRepository extends JpaRepository<OtpToken, Long>{
    Optional<OtpToken> findByOtpAndUser(String otp, Users user);
}
