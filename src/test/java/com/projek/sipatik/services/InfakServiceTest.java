package com.projek.sipatik.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.projek.sipatik.dto.SetorInfakRequest;
import com.projek.sipatik.exception.FieldValidationException;
import com.projek.sipatik.models.SetorInfak;
import com.projek.sipatik.models.StatusInfak;
import com.projek.sipatik.models.Users;
import com.projek.sipatik.repositories.SetorInfakRepository;
import com.projek.sipatik.repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class InfakServiceTest {

    @Mock
    private SetorInfakRepository setorInfakRepository;

    @Mock
    private UserRepository userRepository;

    private InfakService infakService;
    private Users alumniAngkatan20;

    @BeforeEach
    void setUp() {
        infakService = new InfakService(setorInfakRepository, userRepository);
        alumniAngkatan20 = Users.builder()
                .id(20L)
                .nama("Alumni Angkatan 20")
                .email("alumni20@example.com")
                .angkatan(20L)
                .jenjang("S1")
                .build();
    }

    @Test
    void angkatan20MenerimaNominalTepatDelapanRatusRibu() {
        assertDoesNotThrow(() -> infakService.validateInfakNominal(alumniAngkatan20, 800_000L));
    }

    @Test
    void angkatan20MenolakNominalSatuRupiahDiBawahBatas() {
        FieldValidationException error = assertThrows(
                FieldValidationException.class,
                () -> infakService.validateInfakNominal(alumniAngkatan20, 799_999L));

        assertEquals("nominal", error.getField());
        assertEquals(
                "Minimal infak untuk angkatan 20 jenjang S1 adalah Rp 800.000",
                error.getMessage());
    }

    @Test
    void inputManualAdminMenormalisasiBankDanMenyimpanJejakAuditTerkonfirmasi() {
        LocalDate tanggalInfak = LocalDate.now().minusDays(1);
        String emailAdmin = "admin@sipatik.id";
        LocalDateTime sebelumPencatatan = LocalDateTime.now();
        when(setorInfakRepository.save(any(SetorInfak.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SetorInfak hasil = infakService.tambahInfakManual(
                alumniAngkatan20,
                " Mandiri ",
                800_000L,
                tanggalInfak,
                emailAdmin);
        LocalDateTime setelahPencatatan = LocalDateTime.now();

        ArgumentCaptor<SetorInfak> captor = ArgumentCaptor.forClass(SetorInfak.class);
        verify(setorInfakRepository).save(captor.capture());
        SetorInfak tersimpan = captor.getValue();

        assertSame(tersimpan, hasil);
        assertSame(alumniAngkatan20, tersimpan.getUser());
        assertEquals("MANDIRI", tersimpan.getBank());
        assertEquals(800_000L, tersimpan.getNominal());
        assertEquals(tanggalInfak, tersimpan.getTanggalInfak());
        assertEquals(SetorInfak.BUKTI_MANUAL, tersimpan.getBuktiTransfer());
        assertEquals(StatusInfak.DIKONFIRMASI, tersimpan.getStatus());
        assertEquals(emailAdmin, tersimpan.getDibuatOleh());
        assertEquals(emailAdmin, tersimpan.getDiprosesOleh());
        assertNotNull(tersimpan.getDibuatPada());
        assertNotNull(tersimpan.getDiprosesPada());
        assertFalse(tersimpan.getDibuatPada().isBefore(sebelumPencatatan));
        assertFalse(tersimpan.getDibuatPada().isAfter(setelahPencatatan));
        assertFalse(tersimpan.getDiprosesPada().isBefore(sebelumPencatatan));
        assertFalse(tersimpan.getDiprosesPada().isAfter(setelahPencatatan));
    }

    @Test
    void inputManualAdminMenolakBankYangTidakDidukungTanpaMenyimpan() {
        FieldValidationException error = assertThrows(
                FieldValidationException.class,
                () -> infakService.tambahInfakManual(
                        alumniAngkatan20,
                        "BRI",
                        800_000L,
                        LocalDate.now(),
                        "admin@sipatik.id"));

        assertEquals("bank", error.getField());
        verify(setorInfakRepository, never()).save(any(SetorInfak.class));
    }

    @Test
    void inputManualAdminMenolakTanggalMasaDepanTanpaMenyimpan() {
        FieldValidationException error = assertThrows(
                FieldValidationException.class,
                () -> infakService.tambahInfakManual(
                        alumniAngkatan20,
                        "BCA",
                        800_000L,
                        LocalDate.now().plusDays(1),
                        "admin@sipatik.id"));

        assertEquals("tanggalInfak", error.getField());
        verify(setorInfakRepository, never()).save(any(SetorInfak.class));
    }

    @Test
    void setoranAlumniMengunciPemilikSebelumMengecekDuplikat() {
        LocalDate tanggal = LocalDate.now();
        SetorInfakRequest request = SetorInfakRequest.builder()
                .bank("BCA")
                .nominal(800_000L)
                .tanggalInfak(tanggal)
                .buktiTransfer(new MockMultipartFile("buktiTransfer", "bukti.png", "image/png", new byte[] { 1 }))
                .build();
        when(userRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(alumniAngkatan20));
        when(setorInfakRepository.countKemungkinanDuplikat(alumniAngkatan20, tanggal, 800_000L)).thenReturn(1L);

        FieldValidationException error = assertThrows(
                FieldValidationException.class,
                () -> infakService.simpanInfakAlumni(alumniAngkatan20, request));

        assertEquals("nominal", error.getField());
        verify(userRepository).findByIdForUpdate(20L);
        verify(setorInfakRepository).countKemungkinanDuplikat(alumniAngkatan20, tanggal, 800_000L);
        verify(setorInfakRepository, never()).save(any(SetorInfak.class));
    }

    @Test
    void setoranTerkonfirmasiHarusDibatalkanSebelumDiedit() {
        SetorInfak infak = SetorInfak.builder()
                .id(71L)
                .user(alumniAngkatan20)
                .status(StatusInfak.DIKONFIRMASI)
                .nominal(800_000L)
                .bank("BCA")
                .tanggalInfak(LocalDate.now())
                .build();
        when(setorInfakRepository.findByIdForUpdate(71L)).thenReturn(Optional.of(infak));

        FieldValidationException error = assertThrows(FieldValidationException.class,
                () -> infakService.koreksiInfak(71L, 900_000L, "BNI", LocalDate.now(), "admin@sipatik.id"));

        assertEquals("status", error.getField());
        verify(setorInfakRepository).findByIdForUpdate(71L);
        verify(setorInfakRepository, never()).save(any(SetorInfak.class));
    }

    @Test
    void setoranTerkonfirmasiHarusDibatalkanSebelumDihapus() {
        SetorInfak infak = SetorInfak.builder()
                .id(72L)
                .user(alumniAngkatan20)
                .status(StatusInfak.DIKONFIRMASI)
                .nominal(800_000L)
                .bank("BCA")
                .tanggalInfak(LocalDate.now())
                .build();
        when(setorInfakRepository.findByIdForUpdate(72L)).thenReturn(Optional.of(infak));

        FieldValidationException error = assertThrows(FieldValidationException.class,
                () -> infakService.hapusInfak(72L));

        assertEquals("status", error.getField());
        verify(setorInfakRepository).findByIdForUpdate(72L);
        verify(setorInfakRepository, never()).delete(any(SetorInfak.class));
    }
}
