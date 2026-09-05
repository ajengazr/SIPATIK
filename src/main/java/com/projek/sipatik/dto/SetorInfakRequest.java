package com.projek.sipatik.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SetorInfakRequest {

    @NotBlank(message = "Bank tujuan wajib dipilih")
    private String bank;

    @NotNull(message = "Nominal tidak boleh kosong")
    @Positive(message = "Nominal harus lebih dari 0")
    private Long nominal;

    /**
     * Tanggal uang benar-benar ditransfer. Sebelumnya tanggal selalu diisi
     * LocalDate.now() saat submit, sehingga transfer akhir bulan yang baru
     * diunggah keesokan harinya tercatat di bulan yang salah.
     * Dikosongkan berarti hari ini.
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @PastOrPresent(message = "Tanggal infak tidak boleh melewati hari ini")
    private LocalDate tanggalInfak;

    @NotNull(message = "Bukti transfer wajib diunggah")
    private MultipartFile buktiTransfer;
}
