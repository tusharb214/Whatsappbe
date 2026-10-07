package com.sitegenius.whatsappbe.controller;

import com.sitegenius.whatsappbe.dto.WhatsAppNumberResponse;
import com.sitegenius.whatsappbe.entity.WhatsAppNumber;
import com.sitegenius.whatsappbe.service.WhatsAppNumberService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/whatsapp-numbers")
public class WhatsAppNumberController {

    private final WhatsAppNumberService whatsappNumberService;

    public WhatsAppNumberController(
            WhatsAppNumberService whatsappNumberService) {
        this.whatsappNumberService = whatsappNumberService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'AGENT')")
    public ResponseEntity<List<WhatsAppNumberResponse>> getAllNumbers() {

        List<WhatsAppNumberResponse> response =
                whatsappNumberService.getAllNumbers()
                        .stream()
                        .map(this::toResponse)
                        .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'AGENT')")
    public ResponseEntity<WhatsAppNumberResponse> getNumberById(
            @PathVariable Long id) {

        return whatsappNumberService
                .getNumberById(id)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/organization/{organizationId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<WhatsAppNumberResponse> createNumber(
            @PathVariable Long organizationId,
            @RequestBody WhatsAppNumber whatsappNumber) {

        WhatsAppNumber createdNumber =
                whatsappNumberService.createNumber(
                        organizationId,
                        whatsappNumber
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toResponse(createdNumber));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<WhatsAppNumberResponse> updateNumber(
            @PathVariable Long id,
            @RequestBody WhatsAppNumber whatsappNumber) {

        WhatsAppNumber updatedNumber =
                whatsappNumberService.updateNumber(
                        id,
                        whatsappNumber
                );

        return ResponseEntity.ok(
                toResponse(updatedNumber)
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<Void> deleteNumber(
            @PathVariable Long id) {

        whatsappNumberService.deleteNumber(id);

        return ResponseEntity.noContent().build();
    }

    private WhatsAppNumberResponse toResponse(
            WhatsAppNumber number) {

        Long organizationId = null;

        if (number.getOrganization() != null) {
            organizationId =
                    number.getOrganization().getId();
        }

        return new WhatsAppNumberResponse(
                number.getId(),
                organizationId,
                number.getPhoneNumber(),
                number.getDisplayName(),
                number.getPhoneNumberId(),
                number.getWabaId(),
                number.getStatus(),
                number.getCreatedAt()
        );
    }
}