package com.projek.sipatik.models;

import java.time.LocalDate;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Pengeluaran {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private JenisPengeluaran jenis; // KAS_TUNAI / BANK

    // @Enumerated(EnumType.STRING)
    private KategoriBeban kategori; // daftar beban divisi

    private String keterangan;

    private Long nominal;

    private LocalDate tanggalPengeluaran;
}
