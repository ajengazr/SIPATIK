# SIPATIK — Sistem Informasi Pencatatan Infak

![Java](https://img.shields.io/badge/Java-21-007396?style=for-the-badge&logo=oracle)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.x-6DB33F?style=for-the-badge&logo=springboot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)
![Thymeleaf](https://img.shields.io/badge/Thymeleaf-3.x-005F0F?style=for-the-badge&logo=thymeleaf)
![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)

> **SIPATIK** (Sistem Informasi Pencatatan Infak) adalah platform keuangan digital untuk pengelolaan setoran infak, disiapkan khusus bagi alumni program Pemberdayaan Umat Berkelanjutan (PUB). Aplikasi menghadirkan otomasi pencatatan, verifikasi, dan pelaporan transaksi infak yang transparan dan mudah dioperasikan.

---

## 🌟 Ringkasan Aplikasi

| Aspek          | Deskripsi                                                                                  |
|----------------|--------------------------------------------------------------------------------------------|
| Target Pengguna | Admin bendahara PUB & Alumni (donatur)                                                    |
| Tujuan Utama    | Memonitor pendapatan infak, mengelola kas, dan menjaga histori transaksi                  |
| Platform        | Web, dibangun dengan Spring Boot, Thymeleaf, dan PostgreSQL                               |
| Keamanan        | Role-based access (ADMIN & USER), JWT authentication, OTP email untuk login admin         |
| Status          | Aktif digunakan sebagai sistem pencatatan infak dan laporan keuangan internal PUB         |

---

## ⚙️ Setup Awal

### 1. Buat database PostgreSQL

Dijalankan sekali saja, sebagai superuser `postgres`:

```
psql -U postgres -h localhost -f db/init-postgres.sql
```

Skrip akan menanyakan password untuk role `sipatik`, lalu membuat role dan database
kosong bernama `sipatik`. Seluruh tabel dibentuk otomatis oleh Hibernate saat aplikasi
pertama kali dijalankan.

### 2. Konfigurasi lokal

Kredensial pengembangan diletakkan di `config/application-local.properties`
(di luar `src/main/resources` supaya **tidak pernah ikut ter-package ke dalam jar**),
dan berkas itu diabaikan git. Profil `local` aktif otomatis lewat
`spring.profiles.default` di `application.properties`, jadi menjalankan aplikasi
dengan cara apa pun — tombol Run di IDE, `mvn spring-boot:run`, maupun `java -jar` —
sama-sama membacanya tanpa flag tambahan.

Di server, set `SPRING_PROFILES_ACTIVE` (mis. `prod`) atau sediakan `JWT_SECRET`
lewat environment variable; berkas local tidak ada di sana.

### 3. Set environment variable (untuk deployment)

Tidak ada kredensial yang disimpan di dalam repository. Sebelum menjalankan aplikasi,
isi variabel berikut (contoh PowerShell):

```
$env:DB_PASSWORD   = "password role sipatik"
$env:MAIL_USERNAME = "alamat-gmail-pengirim-otp"
$env:MAIL_PASSWORD = "app password gmail"
$env:JWT_SECRET    = "kunci acak minimal 32 karakter"
$env:COOKIE_SECURE = "false" # hanya untuk pengembangan lokal via HTTP
```

| Variabel | Default | Keterangan |
|----------|---------|------------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/sipatik` | Alamat database |
| `DB_USERNAME` | `sipatik` | Role database |
| `DB_PASSWORD` | *(kosong)* | Wajib diisi |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | *(kosong)* | Akun SMTP pengirim OTP admin |
| `JWT_SECRET` | *(tidak ada)* | **Wajib diisi**; aplikasi gagal start bila kosong |
| `COOKIE_SECURE` | `true` | Pertahankan `true` pada HTTPS; gunakan `false` hanya untuk HTTP lokal |
| `JPA_DDL_AUTO` | `update` | Set `validate` bila skema sudah stabil |
| `SERVER_PORT` | `8085` | Port aplikasi |

Tidak ada akun admin bawaan. Untuk membuat admin pertama pada database kosong,
aktifkan bootstrap hanya pada satu kali startup:

```
$env:APP_BOOTSTRAP_ADMIN_ENABLED  = "true"
$env:APP_BOOTSTRAP_ADMIN_NAME     = "Nama Bendahara"
$env:APP_BOOTSTRAP_ADMIN_EMAIL    = "admin@example.org"
$env:APP_BOOTSTRAP_ADMIN_PASSWORD = "password kuat dan unik"
```

`APP_BOOTSTRAP_ADMIN_EMAIL` dan `APP_BOOTSTRAP_ADMIN_PASSWORD` wajib diisi ketika
bootstrap aktif. Setelah akun terbentuk, kembalikan
`APP_BOOTSTRAP_ADMIN_ENABLED=false` dan hapus password bootstrap dari environment.
Jika email tersebut sudah terdaftar, data akun yang ada tidak ditimpa.

### 4. Jalankan

```
./mvnw spring-boot:run
```

Aplikasi tersedia di `http://localhost:8085`.

Jika memakai konfigurasi lokal yang sengaja diabaikan Git pada
`src/main/resources/application-local.properties`, aktifkan profilnya secara eksplisit:

```
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

Pesan `Could not resolve placeholder 'JWT_SECRET'` berarti aplikasi dijalankan tanpa
profil tersebut dan environment variable `JWT_SECRET` belum diisi. Kegagalan start ini
disengaja agar aplikasi tidak pernah memakai kunci JWT bawaan yang mudah ditebak.

> **Catatan migrasi.** `SchemaMigrationRunner` berjalan idempoten pada setiap startup.
> Untuk data hasil migrasi MySQL, runner mengisi kolom `status` pada `setor_infak` dari
> kolom boolean `dikonfirmasi` lalu membuang kolom lama. Runner juga menormalkan email
> pengguna dan memastikan constraint email unik. Startup akan dihentikan bila ditemukan
> email duplikat setelah normalisasi agar operator dapat memperbaikinya tanpa menimpa akun.

---

## 🧭 Alur Penggunaan

### 👩‍💼 Admin Bendahara
1. **Login Admin** — autentikasi dua langkah: email + OTP yang dikirim melalui SMTP.
2. **Dashboard** — meninjau ringkasan kas, total infak, setoran terakhir, dan status kas per kanal bank.
3. **Manajemen Alumni** — menambah dan mengedit identitas dasar alumni, memfilter per angkatan, serta membuka riwayat infak individu. Untuk akun yang belum aktif, klik ikon kunci, pilih **Buat undangan**, lalu salin URL rahasia kepada alumni yang dituju. Membuat ulang undangan langsung membatalkan URL sebelumnya.
4. **Kelola Infak** — melakukan konfirmasi/tolak setoran, mengubah nominal, serta menambah setoran manual bila alumni melakukan transfer offline.
5. **Pengeluaran & Laporan Kas** — menginput pengeluaran kategori beban, menetapkan kas awal/akhir bulanan, dan memantau total saldo.
6. **Error Monitoring** — apabila terjadi permintaan ke rute yang tidak tersedia, sistem menampilkan halaman error tematik (401/403/404/500) sesuai konteks.

### 🎓 Alumni
1. **Aktivasi Akun** — buka URL undangan dari admin, pastikan nama dan angkatan yang tampil benar, lalu isi email, nomor WhatsApp, jenjang, dan password. URL berlaku 7 hari dan hanya dapat dipakai sekali.
2. **Login User** — masuk menggunakan akun alumnus terdaftar, token JWT disimpan pada cookie.
3. **Dashboard User** — melihat status infak bulan berjalan, total transaksi, dan setoran terakhir.
4. **Setor Infak** — mengisi form setor, memilih bank, memasukkan nominal, serta mengunggah bukti transfer.
5. **Menunggu Konfirmasi** — status akan berubah menjadi “Dikonfirmasi” setelah admin memvalidasi nominal.
6. **Rekap Infak** — meninjau histori transaksi terkonfirmasi secara kronologis.
7. **Pengaturan Akun** — memperbarui profil dan kata sandi dengan validasi keamanan di `UserService`.

---

## ✨ Fitur Lengkap

### 1. Autentikasi & Keamanan
- JWT-based authentication untuk semua role.
- OTP email khusus admin setiap sesi login.
- Konfigurasi Spring Security dengan `SecurityFilterChain` untuk mengarahkan respon 401/403 sesuai medium (API vs halaman web).
- Token browser disimpan di cookie `jwt` dengan `HttpOnly`, `SameSite=Lax`, dan
  `Secure` secara default, lalu dibaca oleh `JwtFilter` pada setiap request.

### 2. Manajemen Alumni
- CRUD admin mengelola identitas dasar alumni (nama, jenjang, dan angkatan); email, nomor HP, dan password diisi alumni sendiri melalui undangan aktivasi.
- Filter angkatan, sort ascending/descending, dan pagination manual via navigasi prev/next.
- Modal detail infak dengan tabel transaksi dan total nominal otomatis.
- Undangan aktivasi dibuat lewat aksi admin ber-CSRF, berlaku 7 hari, hanya dapat dipakai sekali, dan pembuatan ulang mencabut undangan lama.

### 3. Pengelolaan Infak
- Tampilan daftar infak dengan filter bulan/tahun/status.
- Konfirmasi/tolak setoran, ubah nominal, hapus setoran, dan tambah setoran manual oleh admin.
- Dropdown angkatan → alumni dengan fetch dinamis (`/admin/api/angkatan-list`, `/admin/api/nama-by-angkatan`).
- Validasi nominal mengikuti standar minimal per angkatan & jenjang melalui `InfakService#validateInfakNominal` untuk form web dan REST.

### 4. Setor Infak User
- Form setor infak dengan upload bukti (tersimpan di folder `uploads/`).
- Validasi server-side: nominal minimal, jenis file, path sanitization.
- Status menunggu konfirmasi sampai admin memproses.

### 5. Pengeluaran & Laporan Kas
- Input pengeluaran harian berdasarkan jenis (`JenisPengeluaran`) dan kategori (`KategoriBeban`).
- Laporan kas bulanan: kas awal, kas akhir, infak lain-lain, pendapatan lain-lain.
- Rekap kas menampilkan ringkasan terperinci untuk pelaporan keuangan PUB.
- Rekonsiliasi membandingkan saldo akhir terhadap saldo awal + pemasukan − pengeluaran, termasuk kanal bank/tunai dan kesinambungan saldo antarbulan.
- Dashboard, laporan, serta ekspor XLSX/PDF memakai perhitungan yang sama dan snapshot transaksi `REPEATABLE_READ`.
- Setoran yang sudah dikonfirmasi harus dibatalkan konfirmasinya sebelum dikoreksi atau dihapus. Mutasi status memakai lock database.

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
│ PostgreSQL + HikariCP Pool  │
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
| `Users`       | id, nama, email unik, password, nomorHp, angkatan, jenjang, role           | Riwayat infak diambil lewat repository |
| `SetorInfak`  | id, user, bank, nominal, buktiTransfer, tanggalInfak, status, jejak proses | `@ManyToOne` ke `Users`               |
| `Pengeluaran` | id, jenis, kategori, nominal, tanggalPengeluaran                             | Stand-alone                           |
| `LaporanKas`  | id, bulan, tahun, kas awal/akhir per bank, pendapatan lain, infak lain-lain | Digunakan dalam pelaporan kas manual  |
| `AdminToken`  | id, email, token, expiredAt                                                 | OTP login admin                       |
| `AlumniInvitation` | tokenHash, expiresAt, usedAt, createdBy                              | Satu undangan terkini per `Users`     |

---

## 🔀 Rute & Endpoint Kunci

### Web (View)
- `/auth/login` — halaman login user
- `/auth/aktivasi?token=…` — menukar token undangan valid dengan cookie aktivasi `HttpOnly`, lalu mengarahkan ke form daftar
- `/auth/daftar` — form aktivasi yang hanya terbuka dengan cookie undangan aktif
- `POST /auth/pendaftaran` — menyelesaikan aktivasi dari identitas pada cookie, tanpa menerima `userId` dari browser
- `/auth-adm/login-admin` — halaman login admin + token email
- `/admin/dash-admin` — dashboard admin
- `/admin/daftar-infak` — daftar setoran infak
- `/admin/alumni` — manajemen alumni
- `/admin/laporan-kas` — laporan keuangan bulanan
- `/user/dash-user` — dashboard alumni
- `/user/rekap-infak` — histori infak terkonfirmasi

### API JSON
- `POST /api/setor-infak` — setor infak melalui multipart dengan bearer JWT
- `POST /admin/infak/tambah` — tambah infak manual melalui form (role ADMIN, CSRF)
- `POST /admin/alumni/{id}/undangan` — membuat URL aktivasi sekali pakai (role ADMIN, CSRF)
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
- `JWT_SECRET` tidak memiliki fallback publik dan wajib disuplai dari environment.
- Bootstrap admin bersifat opt-in serta tidak memiliki email atau password bawaan.
- Reset password menerbitkan token acak sekali pakai setelah OTP valid; hanya hash
  token yang disimpan dan token browser dikirim melalui cookie `HttpOnly`.
- Pengiriman OTP memakai POST + CSRF, cooldown 60 detik, dan mencabut kode lama. Lima percobaan OTP salah mengunci kode tersebut; token reset berlaku 10 menit dan hanya dapat dikonsumsi sekali.
- Password baru minimal 8 karakter dan maksimal 72 byte UTF-8 (batas BCrypt). Login tetap menerima password pendek dari akun lama agar migrasi tidak mengunci pemiliknya.
- Email dinormalisasi menjadi huruf kecil tanpa spasi tepi. Startup memeriksa duplikat sebelum memasang constraint unik; data yang bertabrakan harus diperbaiki operator.
- Registrasi hanya dapat mengisi akun role USER yang email, password, dan nomor HP-nya masih kosong. ID admin, akun aktif, dan akun terisi sebagian tidak dapat ditimpa melalui endpoint pendaftaran.
- Aktivasi akun alumni kosong memakai tautan undangan sekali pakai dari admin. Token acak 256-bit hanya disimpan sebagai hash, berlaku 7 hari, otomatis membatalkan undangan lama, dan identitas alumni ditentukan server tanpa menerima `userId` dari browser.
- Middleware `JwtFilter` memverifikasi token dan mengatur `SecurityContext`.
- Admin login memerlukan OTP (entitas `AdminToken`), meminimalkan risiko kredensial bocor.
- Validasi input ketat untuk form (nominal, bukti transfer, tanggal) serta sanitasi nama file.

---

## ⚙️ Quality Assurance
- **Test suite** — `./mvnw clean test` menjalankan unit/integration test dengan H2 mode PostgreSQL, tanpa koneksi Supabase atau SMTP nyata. Pengujian mencakup rekonsiliasi, ekspor, CSRF/role, registrasi, OTP/reset sekali pakai, dan migrasi skema.
- **Logging** — Menggunakan SLF4J, menggantikan `System.out.println`, memastikan trace log rapi di seluruh service/controller.
- **Validation** — Memanfaatkan `jakarta.validation` (`@NotNull`, `@Positive`, `@NotBlank`) di DTO seperti `AdminSetorInfakRequest` dan `SetorInfakRequest`.
- **Compile Check** — `mvn compile` memastikan konsistensi build setiap iterasi pengembangan.

---

## 🧪 Skenario Pengujian Penggunaan

1. **Setor Infak oleh Alumni**
   - Login user → buka form setor → isi nominal di atas minimal → unggah bukti → submit → muncul popup sukses → admin memvalidasi → status berubah "Dikonfirmasi".

2. **Tambah Infak Manual oleh Admin**
   - Klik “+ Tambah Infak” → pilih angkatan → pilih alumni → isi bank, nominal, tanggal → submit → validasi nominal oleh `InfakService` → data tampil di tabel infak dengan label “Input Manual Admin”.

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
| Database          | PostgreSQL 17+ + HikariCP                                                 |
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
