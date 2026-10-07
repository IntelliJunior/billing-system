package com.billing.controller;

import com.billing.dto.ChangePasswordRequest;
import com.billing.dto.MeResponse;
import com.billing.service.UserAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Login and logout are handled by Spring Security (/api/auth/login, /api/auth/logout)
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserAccountService accounts;

    @GetMapping("/me")
    public MeResponse me(Authentication auth) {
        return accounts.me(auth.getName());
    }

    @PostMapping("/change-password")
    public Map<String, String> changePassword(Authentication auth,
                                              @Valid @RequestBody ChangePasswordRequest req) {
        accounts.changePassword(auth.getName(), req.getCurrentPassword(), req.getNewPassword());
        return Map.of("message", "Password changed successfully");
    }
}
