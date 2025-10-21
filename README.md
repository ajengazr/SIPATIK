# SIPATIK — Sistem Informasi Pencatatan Infak

![Java](https://img.shields.io/badge/Java-21-007396?style=for-the-badge&logo=oracle)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.x-6DB33F?style=for-the-badge&logo=springboot)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=for-the-badge&logo=mysql)
![Thymeleaf](https://img.shields.io/badge/Thymeleaf-3.x-005F0F?style=for-the-badge&logo=thymeleaf)
![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)

> **SIPATIK** (Sistem Informasi Pencatatan Infak) adalah platform keuangan digital untuk pengelolaan setoran infak, disiapkan khusus bagi alumni program Pemberdayaan Umat Berkelanjutan (PUB). Aplikasi menghadirkan otomasi pencatatan, verifikasi, dan pelaporan transaksi infak yang transparan dan mudah dioperasikan.

---

## 🌟 Ringkasan Aplikasi

| Aspek          | Deskripsi                                                                                  |
|----------------|--------------------------------------------------------------------------------------------|
| Target Pengguna | Admin bendahara PUB & Alumni (donatur)                                                    |
| Tujuan Utama    | Memonitor pendapatan infak, mengelola kas, dan menjaga histori transaksi                  |
| Platform        | Web, dibangun dengan Spring Boot, Thymeleaf, dan MySQL                                    |
| Keamanan        | Role-based access (ADMIN & USER), JWT authentication, OTP email untuk login admin         |
| Status          | Aktif digunakan sebagai sistem pencatatan infak dan laporan keuangan internal PUB         |

---

## 🧭 Alur Penggunaan

### 👩‍💼 Admin Bendahara
1. **Login Admin** — autentikasi dua langkah: email + OTP yang dikirim melalui SMTP.
2. **Dashboard** — meninjau ringkasan kas, total infak, setoran terakhir, dan status kas per kanal bank.
3. **Manajemen Alumni** — menambah, mengedit, memfilter alumni per angkatan, dan membuka riwayat infak individu.
4. **Kelola Infak** — melakukan konfirmasi/tolak setoran, mengubah nominal, serta menambah setoran manual bila alumni melakukan transfer offline.
5. **Pengeluaran & Laporan Kas** — menginput pengeluaran kategori beban, menetapkan kas awal/akhir bulanan, dan memantau total saldo.
6. **Error Monitoring** — apabila terjadi permintaan ke rute yang tidak tersedia, sistem menampilkan halaman error tematik (401/403/404/500) sesuai konteks.

### 🎓 Alumni
1. **Login User** — masuk menggunakan akun alumnus terdaftar, token JWT disimpan pada cookie.
2. **Dashboard User** — melihat status infak bulan berjalan, total transaksi, dan setoran terakhir.
3. **Setor Infak** — mengisi form setor, memilih bank, memasukkan nominal, serta mengunggah bukti transfer.
4. **Menunggu Konfirmasi** — status akan berubah menjadi “Dikonfirmasi” setelah admin memvalidasi nominal.
5. **Rekap Infak** — meninjau histori transaksi terkonfirmasi secara kronologis.
6. **Pengaturan Akun** — memperbarui profil dan kata sandi dengan validasi keamanan di `UserService`.

---

## ✨ Fitur Lengkap

### 1. Autentikasi & Keamanan
- JWT-based authentication untuk semua role.
- OTP email khusus admin setiap sesi login.
- Konfigurasi Spring Security dengan `SecurityFilterChain` untuk mengarahkan respon 401/403 sesuai medium (API vs halaman web).
- Token disimpan di cookie `jwt` yang dibaca oleh `JwtFilter` pada setiap request.

### 2. Manajemen Alumni
- CRUD data alumni meliputi nama, jenjang, angkatan, email, nomor HP.
- Filter angkatan, sort ascending/descending, dan pagination manual via navigasi prev/next.
- Modal detail infak dengan tabel transaksi dan total nominal otomatis.

### 3. Pengelolaan Infak
- Tampilan daftar infak dengan filter bulan/tahun/status.
- Konfirmasi/tolak setoran, ubah nominal, hapus setoran, dan tambah setoran manual oleh admin.
- Dropdown angkatan → alumni dengan fetch dinamis (`/admin/api/angkatan-list`, `/admin/api/nama-by-angkatan`).
- Validasi nominal mengikuti standar minimal per angkatan & jenjang (logika berada di `UserService#validateInfakNominal`).

### 4. Setor Infak User
- Form setor infak dengan upload bukti (tersimpan di folder `uploads/`).
- Validasi server-side: nominal minimal, jenis file, path sanitization.
- Status menunggu konfirmasi sampai admin memproses.

### 5. Pengeluaran & Laporan Kas
- Input pengeluaran harian berdasarkan jenis (`JenisPengeluaran`) dan kategori (`KategoriBeban`).
- Laporan kas bulanan: kas awal, kas akhir, infak lain-lain, pendapatan lain-lain.
- Rekap kas menampilkan ringkasan terperinci untuk pelaporan keuangan PUB.

### 6. Error Handling & Observability
- Halaman khusus 401/403/404/500 dengan pesan kontekstual dan CTA navigasi.
- `CustomErrorController` menyediakan fallback untuk semua error HTTP dan mem-bypass whitelabel error Spring.
- `GlobalExceptionHandler` memisahkan respon HTML dan JSON agar API tetap memberikan payload terstruktur (`ErrorResponse`).

### 7. Dashboard & Insight
- Dashboard admin: ringkasan kas bank, bar chart data infak bulanan (tersedia via layanan `AdminService`).
- Dashboard user: status infak bulan berjalan, total transaksi, dan nominal terakhir.

---

## 🧱 Arsitektur Teknis

```
┌──────────────────────────────┐
│        Presentation          │
│  Thymeleaf, HTML5/CSS, JS    │
└──────────────┬──────────────┘
               │
┌──────────────▼──────────────┐
│        Service Layer         │
│ Controllers, Services, DTOs │
│ Spring MVC + Spring Security│
└──────────────┬──────────────┘
               │
┌──────────────▼──────────────┐
│           Data Layer         │
│ Spring Data JPA + Hibernate │
│ MySQL + HikariCP Pooling    │
└──────────────┬──────────────┘
               │
┌──────────────▼──────────────┐
│        External Services     │
│ JavaMail (OTP), OpenPDF,     │
│ Apache POI, File Storage     │
└──────────────────────────────┘
```

---

## 🗃️ Entitas Data Utama

| Entity        | Fields Kunci                                                                 | Relasi                                |
|---------------|------------------------------------------------------------------------------|---------------------------------------|
| `Users`       | id, nama, email, password, nomorHp, angkatan, jenjang, role                 | `@OneToMany` ke `SetorInfak`          |
| `SetorInfak`  | id, user, bank, nominal, buktiTransfer, tanggalInfak, dikonfirmasi          | `@ManyToOne` ke `Users`               |
| `Pengeluaran` | id, jenis, kategori, nominal, tanggalPengeluaran                             | Stand-alone                           |
| `LaporanKas`  | id, bulan, tahun, kas awal/akhir per bank, pendapatan lain, infak lain-lain | Digunakan dalam pelaporan kas manual  |
| `AdminToken`  | id, email, token, expiredAt                                                 | OTP login admin                       |

---

## 🔀 Rute & Endpoint Kunci

### Web (View)
- `/auth/login` — halaman login user
- `/auth-adm/login` — halaman login admin + OTP
- `/admin/dash-admin` — dashboard admin
- `/admin/daftar-infak` — daftar setoran infak
- `/admin/alumni` — manajemen alumni
- `/admin/laporan-kas` — laporan keuangan bulanan
- `/user/dash-user` — dashboard alumni
- `/user/rekap-infak` — histori infak terkonfirmasi

### API JSON
- `POST /api/auth/login` — login user (mengembalikan JWT)
- `POST /admin/infak/tambah` — tambah infak manual (role ADMIN)
- `GET /admin/api/angkatan-list` — daftar angkatan unik
- `GET /admin/api/nama-by-angkatan` — daftar alumni sebuah angkatan beserta ID
- `POST /admin/check-user` — validasi keberadaan user sesuai nama & angkatan

### Error Routing
- `Response.sendError(401/403)` dari `SecurityConfig` → diarahkan ke `CustomErrorController`
- `NoHandlerFoundException` → `GlobalExceptionHandler` menampilkan halaman 404 atau JSON 404
- `server.error.whitelabel.enabled=false` memastikan halaman custom selalu digunakan

---

## 🎛️ Antarmuka Pengguna

| Modul                | Highlight Penggunaan                                                                                                 |
|----------------------|----------------------------------------------------------------------------------------------------------------------|
| Sidebar Admin        | Navigasi cepat antar modul: Dashboard, Alumni, Pengeluaran, Infak, Laporan Kas                                       |
| Modal Tambah Infak   | Dropdown dinamis (angkatan → nama alumni), validasi nominal, input tanggal otomatis ke tanggal hari ini              |
| Modal Detail Alumni  | Tabel riwayat infak + total nominal otomatis, menampilkan hanya transaksi yang `dikonfirmasi`                        |
| Filter Infak         | Kombinasi filter bulan, tahun, status; memanfaatkan query parameter agar state dapat dibagikan (shareable URL)      |
| Dashboard User       | Progress ring status infak dan card ringkasan (jumlah transaksi, nominal total, status bulan berjalan)              |
| Halaman Error        | Desain tematik per status (401, 403, 404, 500) dengan CTA sesuai konteks (kembali, beranda, login)                   |

---

## 🔐 Kebijakan Keamanan
- Password disimpan menggunakan BCrypt (`PasswordEncoder`).
- JWT dikelola oleh `JwtUtil`, disisipkan ke header `Authorization` atau cookie `jwt`.
- Middleware `JwtFilter` memverifikasi token dan mengatur `SecurityContext`.
- Admin login memerlukan OTP (entitas `AdminToken`), meminimalkan risiko kredensial bocor.
- Validasi input ketat untuk form (nominal, bukti transfer, tanggal) serta sanitasi nama file.

---

## ⚙️ Quality Assurance
- **Unit Test** — `SipatikApplicationTests` sebagai baseline, dapat dikembangkan menjadi integrasi test modul.
- **Logging** — Menggunakan SLF4J, menggantikan `System.out.println`, memastikan trace log rapi di seluruh service/controller.
- **Validation** — Memanfaatkan `jakarta.validation` (`@NotNull`, `@Positive`, `@NotBlank`) di DTO seperti `AdminSetorInfakRequest` dan `SetorInfakRequest`.
- **Compile Check** — `mvn compile` memastikan konsistensi build setiap iterasi pengembangan.

---

## 🧪 Skenario Pengujian Penggunaan

1. **Setor Infak oleh Alumni**
   - Login user → buka form setor → isi nominal di atas minimal → unggah bukti → submit → muncul popup sukses → admin memvalidasi → status berubah "Dikonfirmasi".

2. **Tambah Infak Manual oleh Admin**
   - Klik “+ Tambah Infak” → pilih angkatan → pilih alumni → isi bank, nominal, tanggal → submit → validasi nominal oleh `UserService` → data tampil di tabel infak dengan label “Input Manual Admin”.

3. **Penanganan Error 404**
   - Saat login admin aktif, akses rute `/admin/salah-route` → tersaji halaman 404 custom dengan informasi rute yang tidak ditemukan.

4. **Pengeluaran Bulanan**
   - Navigasi ke Pengeluaran → pilih kategori & nominal → simpan → data tampil di daftar pengeluaran dan tercermin pada laporan kas.

5. **Laporan Kas**
   - Buka Laporan Kas → atur bulan & tahun → sistem menampilkan kas awal/akhir, pemasukan, pengeluaran → update angka → flash message sukses.

---

## 📚 Teknologi & Library

| Kategori          | Teknologi                                                                 |
|-------------------|----------------------------------------------------------------------------|
| Bahasa            | Java 21                                                                    |
| Framework         | Spring Boot 3.5.x, Spring MVC, Spring Security                            |
| Template Engine   | Thymeleaf, HTML5, CSS3, JavaScript                                        |
| Database          | MySQL 8 + HikariCP                                                        |
| Persistence       | Spring Data JPA, Hibernate                                                |
| Utils             | Lombok, JavaMail, OpenPDF, Apache POI                                     |
| Build Tool        | Maven                                                                     |
| Testing           | JUnit (melalui starter Spring Boot)                                       |

---

## 🛤️ Roadmap & Pengembangan Lanjutan
- Penyimpanan cloud untuk bukti transfer dan arsip dokumen.
- Integrasi notifikasi (WA/Telegram) untuk status infak.
- Dashboard analitik dengan chart interaktif (React/Chart.js).
- Audit trail terperinci untuk perubahan data sensitif.
- Implementasi multi-tenancy jika digunakan lintas organisasi.

---

## 👥 Kontributor
- **Ajeng Azzahra Maharani** — Lead developer & maintainer SIPATIK.

> Kontribusi, bug report, dan ide peningkatan selalu terbuka melalui fitur issue/pull request repository ini.

---

**SIPATIK — Pemberdayaan Umat Berkelanjutan** 🕌