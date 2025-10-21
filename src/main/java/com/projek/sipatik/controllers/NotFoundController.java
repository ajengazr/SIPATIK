package com.projek.sipatik.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class NotFoundController {

    @RequestMapping("/halaman-tidak-ada")
    public String notFound(Model model) {
        model.addAttribute("errorMessage", "Halaman tidak ditemukan");
        model.addAttribute("statusCode", 404);
        return "html/error/404";
    }
}
