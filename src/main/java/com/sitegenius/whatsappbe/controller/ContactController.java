package com.sitegenius.whatsappbe.controller;

import com.sitegenius.whatsappbe.dto.contact.ContactResponse;
import com.sitegenius.whatsappbe.entity.Contact;
import com.sitegenius.whatsappbe.service.ContactService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'AGENT')")
    public ResponseEntity<List<ContactResponse>> getAllContacts() {

        return ResponseEntity.ok(
                contactService.getAllContacts()
                        .stream()
                        .map(this::toResponse)
                        .toList()
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'AGENT')")
    public ResponseEntity<ContactResponse> getContactById(
            @PathVariable Long id) {

        return contactService.getContactById(id)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ContactResponse> createContact(
            @RequestBody Contact contact) {

        Contact createdContact =
                contactService.createContact(contact);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toResponse(createdContact));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ContactResponse> updateContact(
            @PathVariable Long id,
            @RequestBody Contact contact) {

        Contact updatedContact =
                contactService.updateContact(id, contact);

        return ResponseEntity.ok(
                toResponse(updatedContact)
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteContact(
            @PathVariable Long id) {

        contactService.deleteContact(id);

        return ResponseEntity.noContent().build();
    }

    private ContactResponse toResponse(Contact contact) {

        Long organizationId = null;

        if (contact.getOrganization() != null) {
            organizationId = contact.getOrganization().getId();
        }

        return new ContactResponse(
                contact.getId(),
                organizationId,
                contact.getName(),
                contact.getPhoneNumber(),
                contact.getEmail(),
                contact.getProfileName(),
                contact.getStatus(),
                contact.getCreatedAt()
        );
    }
}