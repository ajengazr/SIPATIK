package com.projek.sipatik.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.projek.sipatik.repositories.UserRepository;

@RestController
@RequestMapping("/api")
public class ApiController {
    @Autowired
    private UserRepository userRepository;

    @GetMapping("/nama-by-angkatan")
    public List<String> getNamaByAngkatan(@RequestParam Long angkatan) {
        return userRepository.findNamaByAngkatan(angkatan);
    }
}
