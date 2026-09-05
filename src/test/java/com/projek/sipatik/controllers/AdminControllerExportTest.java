package com.projek.sipatik.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import com.projek.sipatik.services.LaporanKasExportService;

@ExtendWith(MockitoExtension.class)
class AdminControllerExportTest {

    @Mock
    private LaporanKasExportService exportService;

    private AdminController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminController();
        ReflectionTestUtils.setField(controller, "laporanKasExportService", exportService);
    }

    @Test
    void excelMengirimAttachmentDenganNamaDanContentTypeYangTepat() {
        byte[] content = { 1, 2, 3 };
        when(exportService.exportExcel(2026, 8)).thenReturn(content);
        when(exportService.excelFilename(2026, 8)).thenReturn("laporan-kas-2026-08.xlsx");

        ResponseEntity<byte[]> response = controller.exportLaporanKasExcel(8, 2026);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"laporan-kas-2026-08.xlsx\"");
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(content.length);
        assertThat(response.getBody()).isSameAs(content);
    }

    @Test
    void pdfMengirimAttachmentDenganNamaDanContentTypeYangTepat() {
        byte[] content = { 4, 5, 6 };
        when(exportService.exportPdf(2026, 8)).thenReturn(content);
        when(exportService.pdfFilename(2026, 8)).thenReturn("laporan-kas-2026-08.pdf");

        ResponseEntity<byte[]> response = controller.exportLaporanKasPdf(8, 2026);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"laporan-kas-2026-08.pdf\"");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(content.length);
        assertThat(response.getBody()).isSameAs(content);
    }

    @Test
    void periodeTidakValidMenghasilkanBadRequestTanpaMembuatBerkas() {
        ResponseEntity<byte[]> response = controller.exportLaporanKasExcel(13, 2026);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(
                new MediaType("text", "plain", StandardCharsets.UTF_8));
        assertThat(new String(response.getBody(), StandardCharsets.UTF_8))
                .isEqualTo("Periode laporan tidak valid.");
        verifyNoInteractions(exportService);
    }
}
