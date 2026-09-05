package com.projek.sipatik.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.projek.sipatik.models.SetorInfak;
import com.projek.sipatik.models.StatusInfak;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bentuk JSON untuk satu setoran infak.
 *
 * Endpoint lama mengembalikan entity SetorInfak apa adanya, sehingga objek Users
 * yang menempel padanya ikut terserialisasi lengkap dengan kolom password.
 * DTO ini hanya membawa field yang memang perlu dilihat klien.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SetorInfakResponse {

    private Long id;
    private Long userId;
    private String namaAlumni;
    private Long angkatan;
    private String jenjang;

    private String bank;
    private Long nominal;
    private LocalDate tanggalInfak;

    private StatusInfak status;
    private String statusLabel;
    private String alasanTolak;

    private String buktiTransfer;
    private boolean inputManual;

    private String diprosesOleh;
    private LocalDateTime diprosesPada;

    public static SetorInfakResponse from(SetorInfak infak) {
        StatusInfak status = infak.getStatus() == null ? StatusInfak.MENUNGGU : infak.getStatus();
        return SetorInfakResponse.builder()
                .id(infak.getId())
                .userId(infak.getUser() != null ? infak.getUser().getId() : null)
                .namaAlumni(infak.getUser() != null ? infak.getUser().getNama() : null)
                .angkatan(infak.getUser() != null ? infak.getUser().getAngkatan() : null)
                .jenjang(infak.getUser() != null ? infak.getUser().getJenjang() : null)
                .bank(infak.getBank())
                .nominal(infak.getNominal())
                .tanggalInfak(infak.getTanggalInfak())
                .status(status)
                .statusLabel(status.getLabel())
                .alasanTolak(infak.getAlasanTolak())
                .buktiTransfer(infak.isInputManual() ? null : infak.getBuktiTransfer())
                .inputManual(infak.isInputManual())
                .diprosesOleh(infak.getDiprosesOleh())
                .diprosesPada(infak.getDiprosesPada())
                .build();
    }
}
