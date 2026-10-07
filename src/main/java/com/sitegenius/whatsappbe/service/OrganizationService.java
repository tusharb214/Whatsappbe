package com.sitegenius.whatsappbe.service;

import com.sitegenius.whatsappbe.entity.Organization;
import com.sitegenius.whatsappbe.entity.OrganizationStatus;
import com.sitegenius.whatsappbe.repository.OrganizationRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    public OrganizationService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    // Get all organizations
    public List<Organization> getAllOrganizations() {
        return organizationRepository.findAll();
    }

    // Get organization by ID
    public Optional<Organization> getOrganizationById(Long id) {
        return organizationRepository.findById(id);
    }

    // Create organization
    public Organization createOrganization(Organization organization) {

        if (organizationRepository.existsByEmail(organization.getEmail())) {
            throw new RuntimeException(
                    "Organization email already exists"
            );
        }

        // Every newly created organization starts as PENDING
        organization.setStatus(OrganizationStatus.PENDING);

        return organizationRepository.save(organization);
    }

    // Update organization
    public Organization updateOrganization(
            Long id,
            Organization updatedOrganization) {

        Organization existingOrganization =
                organizationRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Organization not found"
                                )
                        );

        existingOrganization.setName(
                updatedOrganization.getName()
        );

        existingOrganization.setEmail(
                updatedOrganization.getEmail()
        );

        return organizationRepository.save(existingOrganization);
    }

    // Approve organization
    public Organization approveOrganization(Long id) {

        Organization organization =
                organizationRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Organization not found"
                                )
                        );

        organization.setStatus(
                OrganizationStatus.APPROVED
        );

        return organizationRepository.save(organization);
    }

    // Reject organization
    public Organization rejectOrganization(Long id) {

        Organization organization =
                organizationRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Organization not found"
                                )
                        );

        organization.setStatus(
                OrganizationStatus.REJECTED
        );

        return organizationRepository.save(organization);
    }

    // Suspend organization
    public Organization suspendOrganization(Long id) {

        Organization organization =
                organizationRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Organization not found"
                                )
                        );

        organization.setStatus(
                OrganizationStatus.SUSPENDED
        );

        return organizationRepository.save(organization);
    }

    // Activate organization
    public Organization activateOrganization(Long id) {

        Organization organization =
                organizationRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Organization not found"
                                )
                        );

        organization.setStatus(
                OrganizationStatus.APPROVED
        );

        return organizationRepository.save(organization);
    }

    // Delete organization
    public void deleteOrganization(Long id) {

        if (!organizationRepository.existsById(id)) {
            throw new RuntimeException(
                    "Organization not found"
            );
        }

        organizationRepository.deleteById(id);
    }
}