package com.projek.sipatik.models;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SetorInfak {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private Users user;

    private String bank;
    private Long nominal;

    private String buktiTransfer;

    /** Tanggal uang benar-benar ditransfer (bisa berbeda dari tanggal input). */
    private LocalDate tanggalInfak;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private StatusInfak status = StatusInfak.MENUNGGU;

    /** Diisi hanya ketika status DITOLAK, supaya alumni tahu alasannya. */
    @Column(length = 255)
    private String alasanTolak;

    // ---- jejak audit ----
    /** Email admin yang mengonfirmasi / menolak setoran ini. */
    private String diprosesOleh;
    private LocalDateTime diprosesPada;
    private LocalDateTime dibuatPada;
    /** Email admin bila baris ini hasil input manual, null bila disetor sendiri oleh alumni. */
    private String dibuatOleh;

    /**
     * Jaring pengaman: kombinasi @Builder.Default dan konstruktor Lombok tidak selalu
     * menerapkan nilai awal, sedangkan status null akan merusak seluruh query laporan.
     */
    @PrePersist
    void defaultkanStatus() {
        if (status == null) {
            status = StatusInfak.MENUNGGU;
        }
        if (dibuatPada == null) {
            dibuatPada = LocalDateTime.now();
        }
    }

    public boolean isDikonfirmasi() {
        return status == StatusInfak.DIKONFIRMASI;
    }

    public boolean isDitolak() {
        return status == StatusInfak.DITOLAK;
    }

    public boolean isMenunggu() {
        return status == null || status == StatusInfak.MENUNGGU;
    }

    /** True bila baris ini diinput manual oleh admin (tidak ada file bukti transfer). */
    public boolean isInputManual() {
        return BUKTI_MANUAL.equals(buktiTransfer);
    }

    public static final String BUKTI_MANUAL = "admin-manual-entry";
}
