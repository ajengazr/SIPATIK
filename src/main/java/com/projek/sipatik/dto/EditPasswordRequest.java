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
public class EditPasswordRequest {
    private String oldPassword;
    private String newPassword;
    private String confirmPassword;

}
