package com.projek.sipatik.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "otp_token")
public class OtpToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String otp;

    private String email;

    private LocalDateTime expiredAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "failed_attempts")
    private Integer failedAttempts = 0;

    private boolean verified = false;

    /**
     * Hanya hash SHA-256 token reset yang disimpan. Token mentah hidup singkat di
     * cookie HttpOnly browser dan tidak pernah ditulis ke database atau log.
     */
    @Column(name = "reset_token_hash", unique = true, length = 64)
    private String resetTokenHash;

    @Column(name = "reset_expires_at")
    private LocalDateTime resetExpiresAt;

    @Column(name = "reset_used")
    private Boolean resetUsed = false;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private Users user;

}
