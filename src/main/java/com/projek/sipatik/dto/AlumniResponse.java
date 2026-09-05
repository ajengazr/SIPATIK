package com.projek.sipatik.dto;

import com.projek.sipatik.models.Users;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bentuk JSON untuk data alumni.
 *
 * Menggantikan pengembalian entity Users mentah, yang ikut membawa kolom
 * password (hash BCrypt) ke response.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlumniResponse {

    private Long id;
    private String nama;
    private String email;
    private String nomorHp;
    private Long angkatan;
    private String jenjang;
    /** False bila angkatan alumni ini belum punya tarif minimal infak. */
    private boolean tarifInfakTersedia;

    public static AlumniResponse from(Users user, boolean tarifInfakTersedia) {
        return AlumniResponse.builder()
                .id(user.getId())
                .nama(user.getNama())
                .email(user.getEmail())
                .nomorHp(user.getNomorHp())
                .angkatan(user.getAngkatan())
                .jenjang(user.getJenjang())
                .tarifInfakTersedia(tarifInfakTersedia)
                .build();
    }
}
