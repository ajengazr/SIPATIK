package com.projek.sipatik.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/** Halaman masuk publik pada root aplikasi. */
@Controller
public class LandingController {

    @GetMapping("/")
    public String landingPage() {
        return "html/landing-page";
    }

    /** Browser boleh meminta favicon implicit; balas bersih tanpa memicu halaman 404. */
    @GetMapping("/favicon.ico")
    @ResponseBody
    public ResponseEntity<Void> favicon() {
        return ResponseEntity.noContent().build();
    }
}
