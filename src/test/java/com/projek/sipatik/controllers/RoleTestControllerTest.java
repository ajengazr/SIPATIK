package com.projek.sipatik.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
public class RoleTestControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testAdminOnlyEndpointWithAdminRole() throws Exception {
        mockMvc.perform(get("/api/test/admin-only"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Anda berhasil mengakses endpoint admin-only"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void testAdminOnlyEndpointWithUserRole() throws Exception {
        mockMvc.perform(get("/api/test/admin-only"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void testUserOnlyEndpointWithUserRole() throws Exception {
        mockMvc.perform(get("/api/test/user-only"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Anda berhasil mengakses endpoint user-only"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testUserOnlyEndpointWithAdminRole() throws Exception {
        mockMvc.perform(get("/api/test/user-only"))
                .andExpect(status().isForbidden());
    }
}



