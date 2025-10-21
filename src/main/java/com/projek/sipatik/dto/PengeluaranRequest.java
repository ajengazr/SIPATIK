package com.projek.sipatik.dto;

import java.time.LocalDateTime;

import com.projek.sipatik.models.JenisPengeluaran;
import com.projek.sipatik.models.KategoriBeban;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PengeluaranRequest {
    private Long id;

    private JenisPengeluaran jenis; // KAS_TUNAI / BANK

    private KategoriBeban kategori; // daftar beban divisi

    private String keterangan;

    private Long nominal;

    private LocalDateTime tanggalPengeluaran;

}
