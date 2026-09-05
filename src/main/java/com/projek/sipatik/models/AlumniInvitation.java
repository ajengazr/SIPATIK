package com.projek.sipatik.models;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Undangan aktivasi terkini untuk satu alumni. Token mentah tidak pernah disimpan. */
@Entity
@Table(name = "alumni_invitation", uniqueConstraints = {
        @UniqueConstraint(name = "uk_alumni_invitation_user", columnNames = "user_id"),
        @UniqueConstraint(name = "uk_alumni_invitation_token_hash", columnNames = "token_hash")
})
@Getter
@Setter
@NoArgsConstructor
public class AlumniInvitation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;
}
