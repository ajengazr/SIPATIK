package com.projek.sipatik.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EditKasRequest {
    
    @NotNull(message = "Bulan tidak boleh kosong")
    @Min(value = 1, message = "Bulan harus antara 1-12")
    @Max(value = 12, message = "Bulan harus antara 1-12")
    private Integer bulan;
    
    @NotNull(message = "Tahun tidak boleh kosong")
    @Min(value = 2000, message = "Tahun tidak valid")
    @Max(value = 2100, message = "Tahun tidak valid")
    private Integer tahun;
    
    @DecimalMin(value = "0.0", message = "Nilai tidak boleh negatif")
    private BigDecimal awalBca;
    
    @DecimalMin(value = "0.0", message = "Nilai tidak boleh negatif")
    private BigDecimal awalMandiri;
    
    @DecimalMin(value = "0.0", message = "Nilai tidak boleh negatif")
    private BigDecimal awalTunai;
    
    @DecimalMin(value = "0.0", message = "Nilai tidak boleh negatif")
    private BigDecimal awalBni;
    
    @DecimalMin(value = "0.0", message = "Nilai tidak boleh negatif")
    private BigDecimal akhirBca;
    
    @DecimalMin(value = "0.0", message = "Nilai tidak boleh negatif")
    private BigDecimal akhirMandiri;
    
    @DecimalMin(value = "0.0", message = "Nilai tidak boleh negatif")
    private BigDecimal akhirTunai;
    
    @DecimalMin(value = "0.0", message = "Nilai tidak boleh negatif")
    private BigDecimal akhirBni;
    
    @DecimalMin(value = "0.0", message = "Nilai tidak boleh negatif")
    private BigDecimal infakLainLain;
    
    @DecimalMin(value = "0.0", message = "Nilai tidak boleh negatif")
    private BigDecimal pendapatanLainLain;
}



