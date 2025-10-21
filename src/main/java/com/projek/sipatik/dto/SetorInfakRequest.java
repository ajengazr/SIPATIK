package com.projek.sipatik.dto;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class    SetorInfakRequest {
    @NotBlank
    private String bank;

    @NotNull
    private Long nominal;

    @NotNull
    private MultipartFile buktiTransfer;
}
