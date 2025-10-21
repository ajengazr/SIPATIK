package com.projek.sipatik.dto;

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
public class UserRequest {
    private String email;
    private String password;
    private String nomorHp;
    private String jenjang;

     public void normalizeNomorWa() {
        if (nomorHp.startsWith("+62")) {
            nomorHp = "0" + nomorHp.substring(3);
        } else if (nomorHp.startsWith("62")) {
            nomorHp = "0" + nomorHp.substring(2);
        }
     }
}
