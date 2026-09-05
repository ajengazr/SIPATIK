package com.projek.sipatik.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Penyesuaian data untuk skema yang berubah, dijalankan sekali setiap aplikasi start.
 *
 * Proyek ini memakai {@code ddl-auto=update}, yang bisa menambah kolom baru tetapi tidak
 * melakukan backfill atau normalisasi data lama. Runner ini memigrasikan kolom
 * {@code status} pada {@code setor_infak}, menormalkan email pengguna, dan memastikan
 * constraint email unik tersedia. Pada database baru langkah-langkah tersebut bersifat
 * idempoten; pada database lama startup sengaja dihentikan bila ada email yang
 * bertabrakan dan memerlukan keputusan operator.
 *
 * Semua langkah dijaga dengan pengecekan information_schema sehingga aman dijalankan
 * berulang kali maupun pada database yang masih kosong.
 */
@Component
@Order(1)
public class SchemaMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrationRunner.class);
    private static final String TABEL_INFAK = "setor_infak";
    private static final String TABEL_USERS = "users";
    private static final String UNIQUE_EMAIL = "uk_users_email";

    private final JdbcTemplate jdbc;

    public SchemaMigrationRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        try {
            if (tabelAda(TABEL_INFAK)) {
                migrasikanStatusDanKolomLama();
            } else {
                log.debug("Tabel {} belum ada, migrasi status dilewati.", TABEL_INFAK);
            }
            if (tabelAda(TABEL_USERS)) {
                pastikanEmailUserUnik();
            }
        } catch (Exception e) {
            // Melanjutkan startup setelah migrasi parsial berbahaya: entity baru tidak
            // lagi mengirim nilai untuk kolom dikonfirmasi, sehingga kolom lama yang
            // masih NOT NULL akan membuat insert berikutnya gagal. Gagalkan startup
            // agar operator segera melihat dan memperbaiki masalah skemanya.
            log.error("Migrasi skema kritis tidak selesai; startup dihentikan.", e);
            throw new IllegalStateException("Migrasi skema kritis gagal", e);
        }
    }

    private void migrasikanStatusDanKolomLama() {
        if (!kolomAda(TABEL_INFAK, "status")) {
            throw new IllegalStateException(
                    "Kolom status belum terbentuk di " + TABEL_INFAK + ". Pastikan migrasi DDL berhasil.");
        }

        boolean punyaKolomLama = kolomAda(TABEL_INFAK, "dikonfirmasi");
        Integer kosong = jdbc.queryForObject("SELECT COUNT(*) FROM " + TABEL_INFAK + " WHERE status IS NULL", Integer.class);
        if (kosong != null && kosong > 0) {
            int terisi;
            if (punyaKolomLama) {
                // CASE WHEN <kolom boolean> berlaku sama di PostgreSQL dan H2.
                // Nilai null pada kolom lama diperlakukan sebagai belum diproses.
                terisi = jdbc.update("UPDATE " + TABEL_INFAK + " SET status = CASE WHEN dikonfirmasi"
                        + " THEN 'DIKONFIRMASI' ELSE 'MENUNGGU' END WHERE status IS NULL");
                log.info("Migrasi status infak: {} baris diisi dari kolom dikonfirmasi lama.", terisi);
            } else {
                terisi = jdbc.update("UPDATE " + TABEL_INFAK + " SET status = 'MENUNGGU' WHERE status IS NULL");
                log.info("Migrasi status infak: {} baris diisi dengan MENUNGGU.", terisi);
            }
        }

        pastikanTidakAdaStatusKosong();

        // Jangan hanya menghapus kolom ketika ada baris yang baru saja di-backfill.
        // Database yang sudah pernah terisi tetapi masih memiliki kolom NOT NULL lama
        // tetap akan menolak insert dari entity baru jika kolom ini dibiarkan.
        if (punyaKolomLama) {
            jdbc.execute("ALTER TABLE " + TABEL_INFAK + " DROP COLUMN dikonfirmasi");
            if (kolomAda(TABEL_INFAK, "dikonfirmasi")) {
                throw new IllegalStateException("Kolom lama dikonfirmasi tidak berhasil dihapus");
            }
            log.info("Migrasi status infak: kolom lama dikonfirmasi berhasil dihapus.");
        }
    }

    private void pastikanTidakAdaStatusKosong() {
        Integer tersisa = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + TABEL_INFAK + " WHERE status IS NULL", Integer.class);
        if (tersisa != null && tersisa > 0) {
            throw new IllegalStateException(
                    "Masih ada " + tersisa + " baris setor_infak tanpa status setelah backfill");
        }
    }

    /**
     * Email adalah identifier login dan subject JWT. Normalisasi serta constraint
     * database mencegah dua pendaftaran paralel menghasilkan identitas ambigu.
     */
    private void pastikanEmailUserUnik() {
        Integer grupDuplikat = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ("
                        + "SELECT LOWER(TRIM(email)) AS email_normalized FROM " + TABEL_USERS + " "
                        + "WHERE email IS NOT NULL AND TRIM(email) <> '' "
                        + "GROUP BY LOWER(TRIM(email)) HAVING COUNT(*) > 1"
                        + ") duplicate_emails",
                Integer.class);
        if (grupDuplikat != null && grupDuplikat > 0) {
            throw new IllegalStateException(
                    "Ada email user duplikat setelah normalisasi. Perbaiki data sebelum aplikasi dijalankan.");
        }

        jdbc.update("UPDATE " + TABEL_USERS + " SET email = NULL "
                + "WHERE email IS NOT NULL AND TRIM(email) = ''");
        jdbc.update("UPDATE " + TABEL_USERS + " SET email = LOWER(TRIM(email)) WHERE email IS NOT NULL");

        if (!constraintAda(TABEL_USERS, UNIQUE_EMAIL)) {
            jdbc.execute("ALTER TABLE " + TABEL_USERS
                    + " ADD CONSTRAINT " + UNIQUE_EMAIL + " UNIQUE (email)");
            log.info("Constraint email user unik berhasil dipastikan.");
        }
    }

    /**
     * PostgreSQL memakai current_schema() untuk menentukan skema aktif, sedangkan MySQL
     * memakai database(). Pemeriksaan di bawah menghindari keduanya dengan hanya
     * mencocokkan nama tabel pada skema yang terlihat oleh koneksi.
     */
    private boolean tabelAda(String tabel) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE LOWER(table_name) = ? AND table_schema NOT IN ('pg_catalog','information_schema')",
                Integer.class, tabel);
        return n != null && n > 0;
    }

    private boolean kolomAda(String tabel, String kolom) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE LOWER(table_name) = ? AND LOWER(column_name) = ? "
                        + "AND table_schema NOT IN ('pg_catalog','information_schema')",
                Integer.class, tabel, kolom);
        return n != null && n > 0;
    }

    private boolean constraintAda(String tabel, String constraint) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints "
                        + "WHERE LOWER(table_name) = ? AND LOWER(constraint_name) = ? "
                        + "AND table_schema NOT IN ('pg_catalog','information_schema')",
                Integer.class, tabel, constraint);
        return n != null && n > 0;
    }
}
