package com.sitegenius.whatsappbe.service;

import com.sitegenius.whatsappbe.dto.auth.LoginRequest;
import com.sitegenius.whatsappbe.dto.auth.LoginResponse;
import com.sitegenius.whatsappbe.entity.User;
import com.sitegenius.whatsappbe.repository.UserRepository;
import com.sitegenius.whatsappbe.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {

        User user = userRepository
                .findByEmail(request.getEmail())
                .orElseThrow(() ->
                        new RuntimeException(
                                "Invalid email or password"
                        )
                );

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword())) {

            throw new RuntimeException(
                    "Invalid email or password"
            );
        }

        String token =
                jwtService.generateToken(user);

        Long organizationId = null;

        if (user.getOrganization() != null) {
            organizationId =
                    user.getOrganization().getId();
        }

        return new LoginResponse(
                token,
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name(),
                organizationId
        );
    }
}