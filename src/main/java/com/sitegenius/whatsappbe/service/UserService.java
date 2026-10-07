package com.sitegenius.whatsappbe.service;

import com.sitegenius.whatsappbe.entity.User;
import com.sitegenius.whatsappbe.enums.Role;
import com.sitegenius.whatsappbe.repository.OrganizationRepository;
import com.sitegenius.whatsappbe.repository.UserRepository;
import com.sitegenius.whatsappbe.security.JwtAuthenticationDetails;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OrganizationRepository organizationRepository;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            OrganizationRepository organizationRepository) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.organizationRepository = organizationRepository;
    }

    // =========================================
    // GET ALL USERS
    // =========================================

    // SUPER_ADMIN -> can see all users
    // ADMIN / AGENT -> can see users from own organization only
    public List<User> getAllUsers() {

        Role currentRole = getCurrentRole();

        if (currentRole == Role.SUPER_ADMIN) {
            return userRepository.findAll();
        }

        Long organizationId = getCurrentOrganizationId();

        return userRepository.findAll()
                .stream()
                .filter(user ->
                        user.getOrganization() != null
                                && user.getOrganization()
                                .getId()
                                .equals(organizationId)
                )
                .toList();
    }

    // =========================================
    // GET USER BY ID
    // =========================================

    // SUPER_ADMIN -> can see any user
    // ADMIN / AGENT -> only own organization
    public Optional<User> getUserById(Long id) {

        Role currentRole = getCurrentRole();

        if (currentRole == Role.SUPER_ADMIN) {
            return userRepository.findById(id);
        }

        Long organizationId = getCurrentOrganizationId();

        return userRepository.findById(id)
                .filter(user ->
                        user.getOrganization() != null
                                && user.getOrganization()
                                .getId()
                                .equals(organizationId)
                );
    }

    // =========================================
    // CREATE SUPER ADMIN
    // =========================================

    // Create the first SUPER_ADMIN
    // This is a temporary bootstrap method.
    public User createSuperAdmin(User user) {

        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException(
                    "User email already exists"
            );
        }

        User superAdmin = new User();

        superAdmin.setName(user.getName());
        superAdmin.setEmail(user.getEmail());

        superAdmin.setPassword(
                passwordEncoder.encode(
                        user.getPassword()
                )
        );

        superAdmin.setRole(Role.SUPER_ADMIN);

        // SUPER_ADMIN is platform-level.
        // It does not belong to an organization.
        superAdmin.setOrganization(null);

        return userRepository.save(superAdmin);
    }

    // =========================================
    // CREATE ORGANIZATION ADMIN
    // =========================================

    // SUPER_ADMIN creates an ADMIN for a specific organization
    public User createOrganizationAdmin(
            Long organizationId,
            User user) {

        // Only ADMIN role can be created through this method
        if (user.getRole() != Role.ADMIN) {
            throw new RuntimeException(
                    "Only ADMIN can be created through this endpoint"
            );
        }

        // Email must be unique
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException(
                    "User email already exists"
            );
        }

        // Find organization
        var organization =
                organizationRepository
                        .findById(organizationId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Organization not found"
                                )
                        );

        // Create ADMIN
        User newAdmin = new User();

        newAdmin.setName(user.getName());
        newAdmin.setEmail(user.getEmail());

        newAdmin.setPassword(
                passwordEncoder.encode(
                        user.getPassword()
                )
        );

        newAdmin.setRole(Role.ADMIN);

        newAdmin.setOrganization(organization);

        // New organization admin is ACTIVE by default
        newAdmin.setStatus(
                com.sitegenius.whatsappbe.entity.UserStatus.ACTIVE
        );

        return userRepository.save(newAdmin);
    }

    // =========================================
    // CREATE ADMIN / AGENT
    // =========================================

    // Create ADMIN / AGENT inside the logged-in ADMIN's organization
    public User createUser(User user) {

        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException(
                    "User email already exists"
            );
        }

        // ADMIN can create only ADMIN or AGENT.
        // SUPER_ADMIN must never be created through this endpoint.
        if (user.getRole() == null) {
            throw new RuntimeException(
                    "User role is required"
            );
        }

        if (user.getRole() == Role.SUPER_ADMIN) {
            throw new RuntimeException(
                    "SUPER_ADMIN cannot be created from this endpoint"
            );
        }

        Long currentOrganizationId =
                getCurrentOrganizationId();

        if (currentOrganizationId == null) {
            throw new RuntimeException(
                    "Organization is required"
            );
        }

        User newUser = new User();

        newUser.setName(user.getName());
        newUser.setEmail(user.getEmail());
        newUser.setRole(user.getRole());

        newUser.setOrganization(
                organizationRepository
                        .findById(currentOrganizationId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Organization not found"
                                )
                        )
        );

        newUser.setPassword(
                passwordEncoder.encode(
                        user.getPassword()
                )
        );

        // New users are ACTIVE by default
        newUser.setStatus(
                com.sitegenius.whatsappbe.entity.UserStatus.ACTIVE
        );

        return userRepository.save(newUser);
    }

    // =========================================
    // UPDATE USER
    // =========================================

    // Update user only inside current organization
    public User updateUser(
            Long id,
            User updatedUser) {

        Long organizationId =
                getCurrentOrganizationId();

        User existingUser =
                userRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "User not found"
                                )
                        );

        if (existingUser.getOrganization() == null ||
                !existingUser
                        .getOrganization()
                        .getId()
                        .equals(organizationId)) {

            throw new RuntimeException(
                    "You cannot update a user from another organization"
            );
        }

        // An ADMIN cannot create/change a user into SUPER_ADMIN.
        if (updatedUser.getRole() == Role.SUPER_ADMIN) {
            throw new RuntimeException(
                    "SUPER_ADMIN role cannot be assigned here"
            );
        }

        existingUser.setName(
                updatedUser.getName()
        );

        existingUser.setEmail(
                updatedUser.getEmail()
        );

        existingUser.setRole(
                updatedUser.getRole()
        );

        // Organization cannot be changed during update.

        if (updatedUser.getPassword() != null &&
                !updatedUser.getPassword().isBlank()) {

            existingUser.setPassword(
                    passwordEncoder.encode(
                            updatedUser.getPassword()
                    )
            );
        }

        return userRepository.save(existingUser);
    }

    // =========================================
    // DELETE USER
    // =========================================

    // Delete user only inside current organization
    public void deleteUser(Long id) {

        Long organizationId =
                getCurrentOrganizationId();

        User user =
                userRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "User not found"
                                )
                        );

        if (user.getOrganization() == null ||
                !user
                        .getOrganization()
                        .getId()
                        .equals(organizationId)) {

            throw new RuntimeException(
                    "You cannot delete a user from another organization"
            );
        }

        userRepository.delete(user);
    }

    // =========================================
    // ACTIVATE USER
    // =========================================

    // Only SUPER_ADMIN endpoint should call this method
    public User activateUser(Long id) {

        User user =
                userRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "User not found"
                                )
                        );

        user.setStatus(
                com.sitegenius.whatsappbe.entity.UserStatus.ACTIVE
        );

        return userRepository.save(user);
    }

    // =========================================
    // DEACTIVATE USER
    // =========================================

    // Only SUPER_ADMIN endpoint should call this method
    public User deactivateUser(Long id) {

        User user =
                userRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "User not found"
                                )
                        );

        // SUPER_ADMIN should not be accidentally deactivated
        if (user.getRole() == Role.SUPER_ADMIN) {
            throw new RuntimeException(
                    "SUPER_ADMIN cannot be deactivated"
            );
        }

        user.setStatus(
                com.sitegenius.whatsappbe.entity.UserStatus.INACTIVE
        );

        return userRepository.save(user);
    }

    // =========================================
    // GET ORGANIZATION ID FROM JWT
    // =========================================

    private Long getCurrentOrganizationId() {

        Authentication authentication =
                getAuthentication();

        Object details =
                authentication.getDetails();

        if (!(details instanceof JwtAuthenticationDetails)) {
            throw new RuntimeException(
                    "JWT authentication details not found"
            );
        }

        JwtAuthenticationDetails jwtDetails =
                (JwtAuthenticationDetails) details;

        return jwtDetails.getOrganizationId();
    }

    // =========================================
    // GET CURRENT USER ROLE
    // =========================================

    private Role getCurrentRole() {

        Authentication authentication =
                getAuthentication();

        String authority =
                authentication.getAuthorities()
                        .stream()
                        .findFirst()
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "User role not found"
                                )
                        )
                        .getAuthority();

        return Role.valueOf(
                authority.replace("ROLE_", "")
        );
    }

    // =========================================
    // GET AUTHENTICATION
    // =========================================

    private Authentication getAuthentication() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null ||
                !authentication.isAuthenticated()) {

            throw new RuntimeException(
                    "User is not authenticated"
            );
        }

        return authentication;
    }
}