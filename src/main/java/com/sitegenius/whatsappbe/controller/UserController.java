package com.sitegenius.whatsappbe.controller;

import com.sitegenius.whatsappbe.dto.user.UserResponse;
import com.sitegenius.whatsappbe.entity.User;
import com.sitegenius.whatsappbe.service.UserService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // =========================================
    // GET ALL USERS
    // =========================================

    // Any authenticated user can view users
    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {

        List<UserResponse> users = userService.getAllUsers()
                .stream()
                .map(this::toUserResponse)
                .toList();

        return ResponseEntity.ok(users);
    }

    // =========================================
    // GET USER BY ID
    // =========================================

    // Any authenticated user can view a user
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(
            @PathVariable Long id) {

        return userService.getUserById(id)
                .map(this::toUserResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // =========================================
    // CREATE USER
    // =========================================

    // Only ADMIN can create users
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<UserResponse> createUser(
            @RequestBody User user) {

        User createdUser =
                userService.createUser(user);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toUserResponse(createdUser));
    }

    // =========================================
    // UPDATE USER
    // =========================================

    // Only ADMIN can update users
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long id,
            @RequestBody User user) {

        User updatedUser =
                userService.updateUser(id, user);

        return ResponseEntity.ok(
                toUserResponse(updatedUser)
        );
    }

    // =========================================
    // DELETE USER
    // =========================================

    // Only ADMIN can delete users
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable Long id) {

        userService.deleteUser(id);

        return ResponseEntity.noContent().build();
    }

    // =========================================
    // ACTIVATE USER
    // =========================================

    // Only SUPER_ADMIN can activate users
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping("/{id}/activate")
    public ResponseEntity<UserResponse> activateUser(
            @PathVariable Long id) {

        User activatedUser =
                userService.activateUser(id);

        return ResponseEntity.ok(
                toUserResponse(activatedUser)
        );
    }

    // =========================================
    // DEACTIVATE USER
    // =========================================

    // Only SUPER_ADMIN can deactivate users
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping("/{id}/deactivate")
    public ResponseEntity<UserResponse> deactivateUser(
            @PathVariable Long id) {

        User deactivatedUser =
                userService.deactivateUser(id);

        return ResponseEntity.ok(
                toUserResponse(deactivatedUser)
        );
    }

    // =========================================
    // TEST
    // =========================================

    @GetMapping("/test")
    public String testUser() {
        return "User API is working!";
    }

    // =========================================
    // USER RESPONSE MAPPER
    // =========================================

    private UserResponse toUserResponse(User user) {

        Long organizationId = null;
        String organizationName = null;

        if (user.getOrganization() != null) {

            organizationId =
                    user.getOrganization().getId();

            organizationName =
                    user.getOrganization().getName();
        }

        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name(),
                user.getStatus().name(),
                organizationId,
                organizationName,
                user.getCreatedAt()
        );
    }

    // =========================================
    // CREATE ORGANIZATION ADMIN
    // =========================================

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/organization/{organizationId}/admin")
    public ResponseEntity<UserResponse> createOrganizationAdmin(
            @PathVariable Long organizationId,
            @RequestBody User user) {

        User createdAdmin =
                userService.createOrganizationAdmin(
                        organizationId,
                        user
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toUserResponse(createdAdmin));
    }
}