package com.example.expensemanager.controller;

import com.example.expensemanager.dto.AuthResponse;
import com.example.expensemanager.dto.LoginRequest;
import com.example.expensemanager.dto.RegisterRequest;
import com.example.expensemanager.service.AuthService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResponse register(@RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }
}