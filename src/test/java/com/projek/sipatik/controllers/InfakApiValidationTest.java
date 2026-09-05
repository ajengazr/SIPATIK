package com.projek.sipatik.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.projek.sipatik.models.Role;
import com.projek.sipatik.models.Users;
import com.projek.sipatik.repositories.SetorInfakRepository;
import com.projek.sipatik.repositories.UserRepository;
import com.projek.sipatik.security.JwtUtil;

@SpringBootTest
@AutoConfigureMockMvc
class InfakApiValidationTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private SetorInfakRepository infak;
    @Autowired private JwtUtil jwt;

    @Test
    @Transactional
    void nominalDiBawahMinimumMemberiJson400DanTidakMencatatSetoran() throws Exception {
        Users user = users.save(Users.builder()
                .nama("API Validation Test")
                .email("api-" + UUID.randomUUID() + "@example.org")
                .role(Role.USER).angkatan(20L).jenjang("S1").build());

        mvc.perform(multipart("/api/setor-infak")
                        .file(new MockMultipartFile("buktiTransfer", "bukti.png", "image/png", new byte[] {1}))
                        .header("Authorization", "Bearer " + jwt.generateToken(user))
                        .param("bank", "BCA")
                        .param("nominal", "500000")
                        .param("tanggalInfak", LocalDate.now().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("message").value(org.hamcrest.Matchers.containsString("800.000")));

        assertThat(infak.countByUser(user)).isZero();
    }
}
