package com.projek.sipatik.dto;

import com.projek.sipatik.models.Role;

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
public class LoginRequest {
    private String email;
    
    private String password;

    private String nama;
    private Long angkatan;
    private Role role;

}
