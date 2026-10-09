package com.sitegenius.whatsappbe.service;

import com.sitegenius.whatsappbe.dto.auth.LoginRequest;
import com.sitegenius.whatsappbe.dto.auth.LoginResponse;
import com.sitegenius.whatsappbe.entity.User;
import com.sitegenius.whatsappbe.repository.UserRepository;
import com.sitegenius.whatsappbe.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.sitegenius.whatsappbe.dto.auth.SignupRequest;
import com.sitegenius.whatsappbe.dto.auth.SignupResponse;
import com.sitegenius.whatsappbe.entity.Organization;
import com.sitegenius.whatsappbe.entity.OrganizationStatus;
import com.sitegenius.whatsappbe.enums.Role;
import com.sitegenius.whatsappbe.repository.OrganizationRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;




@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OrganizationRepository organizationRepository;

    public AuthService(
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {

        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }



    @Transactional
    public SignupResponse signup(SignupRequest request) {

        String email = request.getEmail()
                .trim()
                .toLowerCase(Locale.ROOT);

        String organizationName = request.getOrganizationName().trim();
        String name = request.getName().trim();

        if (userRepository.existsByEmail(email)
                || organizationRepository.existsByEmail(email)) {
            throw new IllegalArgumentException(
                    "This email is already registered."
            );
        }

        Organization organization = new Organization();
        organization.setName(organizationName);
        organization.setEmail(email);
        organization.setStatus(OrganizationStatus.PENDING);

        organization = organizationRepository.save(organization);

        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.ADMIN);
        user.setOrganization(organization);
        user.setStatus(com.sitegenius.whatsappbe.entity.UserStatus.ACTIVE);

        user = userRepository.save(user);

        return new SignupResponse(
                "Account created successfully. Your organization is pending approval.",
                user.getId(),
                organization.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name()
        );
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