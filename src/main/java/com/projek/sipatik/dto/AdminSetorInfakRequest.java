package com.projek.sipatik.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class AdminSetorInfakRequest {
    @NotNull(message = "Alumni harus dipilih")
    private Long userId;
    
    @NotNull(message = "Angkatan tidak boleh kosong")
    @Positive(message = "Angkatan harus lebih dari 0")
    private Long angkatan;
    
    @NotBlank(message = "Bank tidak boleh kosong")
    private String bank;
    
    @NotNull(message = "Nominal tidak boleh kosong")
    @Positive(message = "Nominal harus lebih dari 0")
    private Long nominal;
    
    @NotNull(message = "Tanggal infak tidak boleh kosong")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate tanggalInfak;
}




