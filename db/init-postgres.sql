-- =====================================================================
-- SIPATIK - pembuatan database PostgreSQL
--
-- Jalankan sekali saja sebagai superuser postgres:
--     psql -U postgres -h localhost -f db/init-postgres.sql
--
-- Skrip ini hanya membuat role dan database kosong. Seluruh tabel dibentuk
-- Hibernate saat aplikasi pertama kali dijalankan (spring.jpa.hibernate.ddl-auto).
--
-- Password TIDAK ditulis di file ini. psql akan menanyakannya saat dijalankan,
-- sehingga tidak ikut ter-commit ke git. Pakai nilai yang sama untuk environment
-- variable DB_PASSWORD ketika menjalankan aplikasi.
-- =====================================================================

\prompt 'Password untuk role sipatik: ' sipatik_password

-- Role aplikasi. Sengaja bukan superuser: aplikasi hanya perlu hak penuh atas
-- database sipatik saja.
CREATE ROLE sipatik WITH LOGIN PASSWORD :'sipatik_password';

CREATE DATABASE sipatik
    WITH OWNER = sipatik
         ENCODING = 'UTF8'
         TEMPLATE = template0;

-- Role sipatik harus bisa membuat tabel di skema public. Sejak PostgreSQL 15,
-- hak CREATE pada skema public tidak lagi diberikan otomatis ke semua role.
\connect sipatik
GRANT ALL ON SCHEMA public TO sipatik;
ALTER SCHEMA public OWNER TO sipatik;

\echo ''
\echo 'Selesai. Database "sipatik" siap dipakai.'
\echo 'Jangan lupa set DB_PASSWORD dengan password yang barusan diisi.'
