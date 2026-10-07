package com.sitegenius.whatsappbe.service;

import com.sitegenius.whatsappbe.entity.Organization;
import com.sitegenius.whatsappbe.entity.Role;
import com.sitegenius.whatsappbe.entity.WhatsAppNumber;
import com.sitegenius.whatsappbe.repository.OrganizationRepository;
import com.sitegenius.whatsappbe.repository.WhatsAppNumberRepository;
import com.sitegenius.whatsappbe.security.JwtAuthenticationDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class WhatsAppNumberService {

    private final WhatsAppNumberRepository whatsappNumberRepository;
    private final OrganizationRepository organizationRepository;

    public WhatsAppNumberService(
            WhatsAppNumberRepository whatsappNumberRepository,
            OrganizationRepository organizationRepository) {

        this.whatsappNumberRepository = whatsappNumberRepository;
        this.organizationRepository = organizationRepository;
    }

    // Get WhatsApp numbers
    public List<WhatsAppNumber> getAllNumbers() {

        Role currentRole = getCurrentRole();

        // SUPER_ADMIN can see all organizations' numbers
        if (currentRole == Role.SUPER_ADMIN) {
            return whatsappNumberRepository.findAll();
        }

        // ADMIN / AGENT can see only their organization's numbers
        Long organizationId = getCurrentOrganizationId();

        if (organizationId == null) {
            throw new RuntimeException("Organization is required");
        }

        return whatsappNumberRepository
                .findByOrganizationId(organizationId);
    }

    // Get WhatsApp number by ID
    public Optional<WhatsAppNumber> getNumberById(Long id) {

        Role currentRole = getCurrentRole();

        // SUPER_ADMIN can access any number
        if (currentRole == Role.SUPER_ADMIN) {
            return whatsappNumberRepository.findById(id);
        }

        // ADMIN / AGENT can access only their organization's number
        Long organizationId = getCurrentOrganizationId();

        if (organizationId == null) {
            return Optional.empty();
        }

        return whatsappNumberRepository
                .findByIdAndOrganizationId(id, organizationId);
    }

    // Create WhatsApp number
    public WhatsAppNumber createNumber(
            Long organizationId,
            WhatsAppNumber whatsappNumber) {

        Role currentRole = getCurrentRole();

        // Only SUPER_ADMIN can select any organization
        if (currentRole != Role.SUPER_ADMIN) {

            Long currentOrganizationId =
                    getCurrentOrganizationId();

            if (currentOrganizationId == null) {
                throw new RuntimeException(
                        "Organization is required"
                );
            }

            // ADMIN / AGENT cannot create for another organization
            if (!currentOrganizationId.equals(organizationId)) {
                throw new RuntimeException(
                        "You cannot create WhatsApp number for another organization"
                );
            }
        }

        // Duplicate phone number check
        if (whatsappNumberRepository
                .existsByPhoneNumber(whatsappNumber.getPhoneNumber())) {

            throw new RuntimeException(
                    "Phone number already exists"
            );
        }

        Organization organization =
                organizationRepository.findById(organizationId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Organization not found"
                                )
                        );

        WhatsAppNumber newNumber =
                new WhatsAppNumber();

        newNumber.setOrganization(organization);
        newNumber.setPhoneNumber(
                whatsappNumber.getPhoneNumber()
        );
        newNumber.setDisplayName(
                whatsappNumber.getDisplayName()
        );
        newNumber.setPhoneNumberId(
                whatsappNumber.getPhoneNumberId()
        );
        newNumber.setWabaId(
                whatsappNumber.getWabaId()
        );
        newNumber.setAccessToken(
                whatsappNumber.getAccessToken()
        );

        // Default status
        if (whatsappNumber.getStatus() == null ||
                whatsappNumber.getStatus().isBlank()) {

            newNumber.setStatus("ACTIVE");

        } else {

            newNumber.setStatus(
                    whatsappNumber.getStatus()
            );
        }

        return whatsappNumberRepository.save(newNumber);
    }

    // Update WhatsApp number
    public WhatsAppNumber updateNumber(
            Long id,
            WhatsAppNumber updatedNumber) {

        Role currentRole = getCurrentRole();

        WhatsAppNumber existingNumber;

        if (currentRole == Role.SUPER_ADMIN) {

            existingNumber =
                    whatsappNumberRepository.findById(id)
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "WhatsApp number not found"
                                    )
                            );

        } else {

            Long organizationId =
                    getCurrentOrganizationId();

            if (organizationId == null) {
                throw new RuntimeException(
                        "Organization is required"
                );
            }

            existingNumber =
                    whatsappNumberRepository
                            .findByIdAndOrganizationId(
                                    id,
                                    organizationId
                            )
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "WhatsApp number not found"
                                    )
                            );
        }

        if (updatedNumber.getPhoneNumber() != null &&
                !updatedNumber.getPhoneNumber().equals(
                        existingNumber.getPhoneNumber())) {

            if (whatsappNumberRepository
                    .existsByPhoneNumber(
                            updatedNumber.getPhoneNumber())) {

                throw new RuntimeException(
                        "Phone number already exists"
                );
            }

            existingNumber.setPhoneNumber(
                    updatedNumber.getPhoneNumber()
            );
        }

        if (updatedNumber.getDisplayName() != null) {
            existingNumber.setDisplayName(
                    updatedNumber.getDisplayName()
            );
        }

        if (updatedNumber.getPhoneNumberId() != null) {
            existingNumber.setPhoneNumberId(
                    updatedNumber.getPhoneNumberId()
            );
        }

        if (updatedNumber.getWabaId() != null) {
            existingNumber.setWabaId(
                    updatedNumber.getWabaId()
            );
        }

        if (updatedNumber.getAccessToken() != null) {
            existingNumber.setAccessToken(
                    updatedNumber.getAccessToken()
            );
        }

        if (updatedNumber.getStatus() != null) {
            existingNumber.setStatus(
                    updatedNumber.getStatus()
            );
        }

        return whatsappNumberRepository.save(
                existingNumber
        );
    }

    // Delete WhatsApp number
    public void deleteNumber(Long id) {

        Role currentRole = getCurrentRole();

        WhatsAppNumber existingNumber;

        if (currentRole == Role.SUPER_ADMIN) {

            existingNumber =
                    whatsappNumberRepository.findById(id)
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "WhatsApp number not found"
                                    )
                            );

        } else {

            Long organizationId =
                    getCurrentOrganizationId();

            if (organizationId == null) {
                throw new RuntimeException(
                        "Organization is required"
                );
            }

            existingNumber =
                    whatsappNumberRepository
                            .findByIdAndOrganizationId(
                                    id,
                                    organizationId
                            )
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "WhatsApp number not found"
                                    )
                            );
        }

        whatsappNumberRepository.delete(
                existingNumber
        );
    }

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