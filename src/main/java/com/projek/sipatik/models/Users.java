package com.projek.sipatik.models;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
public class Users {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nama;
    private String email;
    private String password;
    private String nomorHp;
    private Long angkatan;
    private String jenjang;

    // Relasi ke SetorInfak sengaja tidak dipetakan dua arah di sini.
    // Versi sebelumnya memakai @OneToMany tanpa mappedBy, yang membuat Hibernate
    // membentuk join table users_setor_infak terpisah dari kolom setor_infak.user_id
    // sehingga ada dua sumber kebenaran untuk relasi yang sama.
    // Riwayat infak seorang alumni diambil lewat SetorInfakRepository.

    @Enumerated(EnumType.STRING)
    private Role role;
}
