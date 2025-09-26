package com.ludogoriesoft.sigmatherm.service;

import com.ludogoriesoft.sigmatherm.service.auth.JwtProvider;
import com.ludogoriesoft.sigmatherm.dto.request.LoginRequest;
import com.ludogoriesoft.sigmatherm.dto.request.RegisterRequest;
import com.ludogoriesoft.sigmatherm.dto.response.LoginResponse;
import com.ludogoriesoft.sigmatherm.model.User;
import com.ludogoriesoft.sigmatherm.model.enums.Role;
import com.ludogoriesoft.sigmatherm.exception.ObjectExistsException;
import com.ludogoriesoft.sigmatherm.exception.ObjectNotFoundException;
import com.ludogoriesoft.sigmatherm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public LoginResponse login(LoginRequest loginRequest) {
        if (!existsByEmail(loginRequest.getEmail())) {
            throw new ObjectNotFoundException("User not found!");
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getEmail(),
                        loginRequest.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtProvider.generateJwtToken(authentication);

        return new LoginResponse(jwt);
    }

    public void createUser(RegisterRequest registerRequest) {
        if (existsByEmail(registerRequest.getEmail())) {
            throw new ObjectExistsException("This email is already taken!");
        }

        User user = User.builder()
                .name(registerRequest.getName())
                .email(registerRequest.getEmail())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .role(Role.USER)
                .enabled(true)
                .build();

        userRepository.save(user);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }
}
