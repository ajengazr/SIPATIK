package com.projek.sipatik.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ConcurrentModel;

import com.projek.sipatik.dto.ErrorResponse;

class GlobalExceptionHandlerSecurityTest {

    @Test
    void apiInternalErrorTidakMembocorkanPesanException() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/gagal");
        String detailInternal = "detail internal sensitif";

        Object result = handler.handleGeneralException(
                new IllegalStateException(detailInternal), request, new ConcurrentModel());

        assertThat(result).isInstanceOf(ResponseEntity.class);
        ResponseEntity<?> response = (ResponseEntity<?>) result;
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isInstanceOf(ErrorResponse.class);
        ErrorResponse error = (ErrorResponse) response.getBody();
        assertThat(error.getMessage())
                .isEqualTo("Terjadi kesalahan pada server, silakan coba lagi")
                .doesNotContain(detailInternal);
    }
}
