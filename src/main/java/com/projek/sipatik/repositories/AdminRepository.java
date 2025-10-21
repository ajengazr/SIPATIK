package com.projek.sipatik.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projek.sipatik.models.Users;

public interface AdminRepository extends JpaRepository<Users, Long>{
    Optional<Users> findByEmail(String email);
}
