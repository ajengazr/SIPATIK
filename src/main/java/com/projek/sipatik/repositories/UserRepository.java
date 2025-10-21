package com.projek.sipatik.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.projek.sipatik.models.Role;
import com.projek.sipatik.models.Users;

@Repository
public interface UserRepository extends JpaRepository<Users, Long> {
    Optional<Users> findByEmail(String email);

    Optional<Users> findByNamaAndAngkatan(String name, Long angkatan);

    Optional<Users> findByNamaAndAngkatanAndEmail(String name, Long angkatan, String email);

    boolean existsByEmail(String Email);

    @Query("SELECT u.nama FROM Users u WHERE u.angkatan = :angkatan")
    List<String> findNamaByAngkatan(@Param("angkatan") Long angkatan);

    @Query("SELECT DISTINCT u.angkatan FROM Users u ORDER BY u.angkatan ASC")
    List<Long> findAllAngkatan();

    @Query("SELECT u.nama FROM Users u WHERE u.angkatan = :angkatan")
    List<String> findNamaByListAngkatan(@Param("angkatan") String angkatan);

    // Ambil semua user dengan role tertentu, urut berdasarkan nama
    List<Users> findByRoleOrderByNamaAsc(Role role);
    
    // Ambil user berdasarkan role + angkatan jika diperlukan
    List<Users> findByRoleAndAngkatanOrderByNamaAsc(Role role, Long angkatan);

    Page<Users> findByRole(Role role, Pageable pageable);

    Page<Users> findByRoleAndAngkatan(Role role, Long angkatan, Pageable pageable);

    // Method untuk mengambil angkatan unik berdasarkan role
    @Query("SELECT DISTINCT u.angkatan FROM Users u WHERE u.role = :role AND u.angkatan IS NOT NULL ORDER BY u.angkatan")
    List<Long> findDistinctAngkatanByRole(@Param("role") Role role);

    // Method untuk mengambil semua user berdasarkan role
    List<Users> findByRole(Role role);

    // Method untuk mengambil angkatan unik berdasarkan role
    // @Query("SELECT DISTINCT u.angkatan FROM Users u WHERE u.role = :role AND u.angkatan IS NOT NULL ORDER BY u.angkatan")
    // List<Long> findDistinctAngkatanByRole(@Param("role") Role role);

    // Method untuk mencari user berdasarkan nama, angkatan, dan role
    Optional<Users> findByNamaAndAngkatanAndRole(String nama, Long angkatan, Role role);
}