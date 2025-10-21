package com.projek.sipatik.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

@Controller
public class CustomErrorController implements ErrorController {
    private static final Logger log = LoggerFactory.getLogger(CustomErrorController.class);

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model,
            @RequestParam(value = "status", required = false) String statusParam) {

        Integer statusCode = resolveStatusCode(request, statusParam);
        HttpStatus httpStatus = HttpStatus.resolve(statusCode != null ? statusCode : 500);
        if (httpStatus == null) {
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        String requestUri = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        Object errorMessageAttr = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        String errorMessage = buildErrorMessage(httpStatus, errorMessageAttr);

        log.debug("Error Controller - status={}, path={}, message={}", httpStatus.value(), requestUri, errorMessage);

        model.addAttribute("statusCode", httpStatus.value());
        model.addAttribute("errorMessage", errorMessage);
        model.addAttribute("path", requestUri);

        switch (httpStatus) {
            case NOT_FOUND:
                return "html/error/404";
            case FORBIDDEN:
                return "html/error/403";
            case UNAUTHORIZED:
                return "html/error/401";
            default:
                return "html/error/500";
        }
    }

    private Integer resolveStatusCode(HttpServletRequest request, String statusParam) {
        if (statusParam != null) {
            try {
                return Integer.parseInt(statusParam);
            } catch (NumberFormatException e) {
                log.warn("Invalid status parameter: {}", statusParam, e);
                return HttpStatus.INTERNAL_SERVER_ERROR.value();
            }
        }

        Object statusAttribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (statusAttribute instanceof Integer) {
            return (Integer) statusAttribute;
        }
        if (statusAttribute != null) {
            try {
                return Integer.valueOf(statusAttribute.toString());
            } catch (NumberFormatException e) {
                log.warn("Unable to parse status attribute: {}", statusAttribute, e);
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }

    private String buildErrorMessage(HttpStatus status, Object errorMessageAttr) {
        if (errorMessageAttr != null && errorMessageAttr.toString() != null
                && !errorMessageAttr.toString().isBlank()) {
            return errorMessageAttr.toString();
        }

        switch (status) {
            case NOT_FOUND:
                return "Halaman tidak ditemukan";
            case FORBIDDEN:
                return "Anda tidak memiliki akses ke halaman ini";
            case UNAUTHORIZED:
                return "Anda perlu login untuk mengakses halaman ini";
            case BAD_REQUEST:
                return "Permintaan tidak valid";
            case INTERNAL_SERVER_ERROR:
            default:
                return "Terjadi kesalahan pada server, silakan coba lagi";
        }
    }
}
