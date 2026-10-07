package com.sitegenius.whatsappbe.service;

import com.sitegenius.whatsappbe.entity.Contact;
import com.sitegenius.whatsappbe.entity.Organization;
import com.sitegenius.whatsappbe.enums.Role;
import com.sitegenius.whatsappbe.repository.ContactRepository;
import com.sitegenius.whatsappbe.repository.OrganizationRepository;
import com.sitegenius.whatsappbe.security.JwtAuthenticationDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ContactService {

    private final ContactRepository contactRepository;
    private final OrganizationRepository organizationRepository;

    public ContactService(
            ContactRepository contactRepository,
            OrganizationRepository organizationRepository) {

        this.contactRepository = contactRepository;
        this.organizationRepository = organizationRepository;
    }

    // =========================================================
    // GET ALL CONTACTS
    // =========================================================

    public List<Contact> getAllContacts() {

        Role currentRole = getCurrentRole();

        if (currentRole == Role.SUPER_ADMIN) {
            return contactRepository.findAll();
        }

        Long organizationId = getCurrentOrganizationId();

        if (organizationId == null) {
            throw new RuntimeException("Organization is required");
        }

        return contactRepository.findByOrganizationId(
                organizationId
        );
    }

    // =========================================================
    // GET CONTACT BY ID
    // =========================================================

    public Optional<Contact> getContactById(Long id) {

        Role currentRole = getCurrentRole();

        if (currentRole == Role.SUPER_ADMIN) {
            return contactRepository.findById(id);
        }

        Long organizationId = getCurrentOrganizationId();

        if (organizationId == null) {
            return Optional.empty();
        }

        return contactRepository.findByIdAndOrganizationId(
                id,
                organizationId
        );
    }

    // =========================================================
    // CREATE CONTACT
    // ADMIN ONLY
    // =========================================================

    public Contact createContact(Contact contact) {

        Role currentRole = getCurrentRole();

        if (currentRole != Role.ADMIN) {
            throw new RuntimeException(
                    "Only ADMIN can create contacts"
            );
        }

        Long organizationId = getCurrentOrganizationId();

        if (organizationId == null) {
            throw new RuntimeException(
                    "Organization is required"
            );
        }

        // -----------------------------------------------------
        // Validate and normalize phone number
        // -----------------------------------------------------

        String normalizedPhoneNumber =
                normalizePhoneNumber(contact.getPhoneNumber());

        if (normalizedPhoneNumber == null ||
                normalizedPhoneNumber.isBlank()) {

            throw new RuntimeException(
                    "Valid phone number is required"
            );
        }

        // -----------------------------------------------------
        // Check duplicate phone number
        // -----------------------------------------------------

        if (contactRepository
                .existsByOrganizationIdAndPhoneNumber(
                        organizationId,
                        normalizedPhoneNumber
                )) {

            throw new RuntimeException(
                    "Contact with this phone number already exists"
            );
        }

        // -----------------------------------------------------
        // Find organization
        // -----------------------------------------------------

        Organization organization =
                organizationRepository.findById(
                        organizationId
                ).orElseThrow(() ->
                        new RuntimeException(
                                "Organization not found"
                        )
                );

        // -----------------------------------------------------
        // Create contact
        // -----------------------------------------------------

        Contact newContact = new Contact();

        newContact.setOrganization(organization);

        newContact.setName(
                contact.getName()
        );

        newContact.setPhoneNumber(
                normalizedPhoneNumber
        );

        newContact.setEmail(
                contact.getEmail()
        );

        newContact.setProfileName(
                contact.getProfileName()
        );

        if (contact.getStatus() == null ||
                contact.getStatus().isBlank()) {

            newContact.setStatus("ACTIVE");

        } else {

            newContact.setStatus(
                    contact.getStatus()
            );
        }

        return contactRepository.save(newContact);
    }

    // =========================================================
    // UPDATE CONTACT
    // ADMIN ONLY
    // =========================================================

    public Contact updateContact(
            Long id,
            Contact updatedContact) {

        Role currentRole = getCurrentRole();

        if (currentRole != Role.ADMIN) {
            throw new RuntimeException(
                    "Only ADMIN can update contacts"
            );
        }

        Long organizationId = getCurrentOrganizationId();

        if (organizationId == null) {
            throw new RuntimeException(
                    "Organization is required"
            );
        }

        // -----------------------------------------------------
        // Find existing contact
        // -----------------------------------------------------

        Contact existingContact =
                contactRepository
                        .findByIdAndOrganizationId(
                                id,
                                organizationId
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Contact not found"
                                )
                        );

        // -----------------------------------------------------
        // Update phone number
        // -----------------------------------------------------

        if (updatedContact.getPhoneNumber() != null &&
                !updatedContact.getPhoneNumber().isBlank()) {

            String normalizedPhoneNumber =
                    normalizePhoneNumber(
                            updatedContact.getPhoneNumber()
                    );

            if (normalizedPhoneNumber == null ||
                    normalizedPhoneNumber.isBlank()) {

                throw new RuntimeException(
                        "Valid phone number is required"
                );
            }

            // Only check duplicate if phone number changed
            if (!normalizedPhoneNumber.equals(
                    existingContact.getPhoneNumber())) {

                if (contactRepository
                        .existsByOrganizationIdAndPhoneNumber(
                                organizationId,
                                normalizedPhoneNumber
                        )) {

                    throw new RuntimeException(
                            "Contact with this phone number already exists"
                    );
                }

                existingContact.setPhoneNumber(
                        normalizedPhoneNumber
                );
            }
        }

        // -----------------------------------------------------
        // Update name
        // -----------------------------------------------------

        if (updatedContact.getName() != null) {

            existingContact.setName(
                    updatedContact.getName()
            );
        }

        // -----------------------------------------------------
        // Update email
        // -----------------------------------------------------

        if (updatedContact.getEmail() != null) {

            existingContact.setEmail(
                    updatedContact.getEmail()
            );
        }

        // -----------------------------------------------------
        // Update profile name
        // -----------------------------------------------------

        if (updatedContact.getProfileName() != null) {

            existingContact.setProfileName(
                    updatedContact.getProfileName()
            );
        }

        // -----------------------------------------------------
        // Update status
        // -----------------------------------------------------

        if (updatedContact.getStatus() != null) {

            existingContact.setStatus(
                    updatedContact.getStatus()
            );
        }

        return contactRepository.save(existingContact);
    }

    // =========================================================
    // DELETE CONTACT
    // ADMIN ONLY
    // =========================================================

    public void deleteContact(Long id) {

        Role currentRole = getCurrentRole();

        if (currentRole != Role.ADMIN) {
            throw new RuntimeException(
                    "Only ADMIN can delete contacts"
            );
        }

        Long organizationId = getCurrentOrganizationId();

        if (organizationId == null) {
            throw new RuntimeException(
                    "Organization is required"
            );
        }

        Contact existingContact =
                contactRepository
                        .findByIdAndOrganizationId(
                                id,
                                organizationId
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Contact not found"
                                )
                        );

        contactRepository.delete(existingContact);
    }

    // =========================================================
    // NORMALIZE PHONE NUMBER
    // =========================================================

    private String normalizePhoneNumber(String phoneNumber) {

        if (phoneNumber == null || phoneNumber.isBlank()) {
            return null;
        }

        /*
         * Remove:
         * + sign
         * spaces
         * hyphens
         * brackets
         * any other non-numeric characters
         *
         * Examples:
         *
         * +919876543210
         * 919876543210
         * +91 9876543210
         * +91-9876543210
         *
         * All become:
         *
         * 919876543210
         */

        return phoneNumber.replaceAll("[^0-9]", "");
    }

    // =========================================================
    // GET ORGANIZATION ID FROM JWT
    // =========================================================

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

    // =========================================================
    // GET CURRENT ROLE
    // =========================================================

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

    // =========================================================
    // GET AUTHENTICATION
    // =========================================================

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