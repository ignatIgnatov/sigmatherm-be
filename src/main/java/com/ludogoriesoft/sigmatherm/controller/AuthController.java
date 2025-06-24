package com.ludogoriesoft.sigmatherm.controller;

import com.ludogoriesoft.sigmatherm.dto.request.LoginRequest;
import com.ludogoriesoft.sigmatherm.dto.request.RegisterRequest;
import com.ludogoriesoft.sigmatherm.dto.response.LoginResponse;
import com.ludogoriesoft.sigmatherm.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> authenticateUser(@RequestBody LoginRequest loginRequest) {
        LoginResponse response = userService.login(loginRequest);
        log.info("Successful login for user with email " + loginRequest.getEmail());
        return ResponseEntity.ok().body(response);
    }

    @PostMapping("/register")
    public ResponseEntity<String> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        userService.createUser(registerRequest);
        log.info("Successful registration for user with email " + registerRequest.getEmail());
        return ResponseEntity.ok("Registration successfully");
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout() {
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok("Logout successful");
    }
}
