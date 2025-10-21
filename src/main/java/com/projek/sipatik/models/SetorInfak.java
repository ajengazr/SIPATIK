package com.projek.sipatik.models;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
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
    @GeneratedValue
    private Long id;

    @ManyToOne
    private Users user;

    private String bank;
    private Long nominal;

    private String buktiTransfer;

    private boolean dikonfirmasi;

    private LocalDate tanggalInfak;
}
