package com.projek.sipatik.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.projek.sipatik.models.AlumniInvitation;

import jakarta.persistence.LockModeType;

public interface AlumniInvitationRepository extends JpaRepository<AlumniInvitation, Long> {
    @Query("SELECT i FROM AlumniInvitation i JOIN FETCH i.user WHERE i.tokenHash = :tokenHash")
    Optional<AlumniInvitation> findByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * Ambil hanya identifier sebelum memperoleh lock. Memuat entity invitation/user
     * pada tahap ini dapat meninggalkan state lama di persistence context ketika
     * transaksi lain sedang menyelesaikan aktivasi token yang sama.
     */
    @Query("SELECT i.user.id FROM AlumniInvitation i WHERE i.tokenHash = :tokenHash")
    Optional<Long> findUserIdByTokenHash(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM AlumniInvitation i JOIN FETCH i.user WHERE i.tokenHash = :tokenHash")
    Optional<AlumniInvitation> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    Optional<AlumniInvitation> findByUserId(Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM AlumniInvitation i WHERE i.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
