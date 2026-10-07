package com.sitegenius.whatsappbe.controller;

import com.sitegenius.whatsappbe.entity.Organization;
import com.sitegenius.whatsappbe.service.OrganizationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    // Only SUPER_ADMIN can view all organizations
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping
    public ResponseEntity<List<Organization>> getAllOrganizations() {
        return ResponseEntity.ok(
                organizationService.getAllOrganizations()
        );
    }

    // Only SUPER_ADMIN can view organization by ID
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<Organization> getOrganizationById(
            @PathVariable Long id) {

        return organizationService.getOrganizationById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Only SUPER_ADMIN can create organization
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping
    public ResponseEntity<Organization> createOrganization(
            @RequestBody Organization organization) {

        Organization createdOrganization =
                organizationService.createOrganization(organization);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(createdOrganization);
    }

    // Only SUPER_ADMIN can update organization
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<Organization> updateOrganization(
            @PathVariable Long id,
            @RequestBody Organization organization) {

        return ResponseEntity.ok(
                organizationService.updateOrganization(id, organization)
        );
    }

    // Approve organization
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping("/{id}/approve")
    public ResponseEntity<Organization> approveOrganization(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                organizationService.approveOrganization(id)
        );
    }

    // Reject organization
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping("/{id}/reject")
    public ResponseEntity<Organization> rejectOrganization(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                organizationService.rejectOrganization(id)
        );
    }

    // Suspend organization
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping("/{id}/suspend")
    public ResponseEntity<Organization> suspendOrganization(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                organizationService.suspendOrganization(id)
        );
    }

    // Activate organization
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping("/{id}/activate")
    public ResponseEntity<Organization> activateOrganization(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                organizationService.activateOrganization(id)
        );
    }

    // Only SUPER_ADMIN can delete organization
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrganization(
            @PathVariable Long id) {

        organizationService.deleteOrganization(id);

        return ResponseEntity.noContent().build();
    }

    // Test API
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/test")
    public String testOrganization() {
        return "Organization API is working!";
    }
}