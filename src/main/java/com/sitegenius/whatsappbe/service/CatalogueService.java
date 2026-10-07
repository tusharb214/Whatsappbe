package com.sitegenius.whatsappbe.service;

import com.sitegenius.whatsappbe.entity.Conversation;
import com.sitegenius.whatsappbe.entity.Organization;
import com.sitegenius.whatsappbe.entity.Product;
import com.sitegenius.whatsappbe.entity.User;
import com.sitegenius.whatsappbe.enums.Role;
import com.sitegenius.whatsappbe.repository.ConversationRepository;
import com.sitegenius.whatsappbe.repository.ProductRepository;
import com.sitegenius.whatsappbe.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CatalogueService {

    private final ConversationRepository conversationRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final WhatsAppCloudApiService whatsAppCloudApiService;

    public CatalogueService(
            ConversationRepository conversationRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            WhatsAppCloudApiService whatsAppCloudApiService
    ) {
        this.conversationRepository = conversationRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.whatsAppCloudApiService = whatsAppCloudApiService;
    }

    // =========================================================
    // VALIDATE CATALOGUE
    // =========================================================

    @Transactional(readOnly = true)
    public List<Product> validateCatalogue(
            Long conversationId,
            List<Long> productIds,
            Authentication authentication
    ) {

        User user =
                getAuthenticatedUser(authentication);

        Conversation conversation =
                getConversationForUser(
                        conversationId,
                        user
                );

        Organization organization =
                conversation.getOrganization();

        if (organization == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Conversation organization is required"
            );
        }

        if (productIds == null ||
                productIds.isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "At least one product is required"
            );
        }

        List<Product> products =
                productRepository
                        .findAllById(productIds);

        // =====================================================
        // PRODUCT COUNT CHECK
        // =====================================================

        if (products.size() != productIds.size()) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "One or more products not found"
            );
        }

        // =====================================================
        // ORGANIZATION + ACTIVE CHECK
        // =====================================================

        for (Product product : products) {

            if (product.getOrganization() == null ||
                    !product.getOrganization()
                            .getId()
                            .equals(organization.getId())) {

                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Product belongs to another organization"
                );
            }

            if (product.getStatus() == null ||
                    !"ACTIVE".equalsIgnoreCase(
                            product.getStatus().trim()
                    )) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Inactive product cannot be sent"
                );
            }
        }

        return products;
    }

    // =========================================================
    // SEND SINGLE PRODUCT
    // =========================================================

    @Transactional
    public String sendProduct(
            Long conversationId,
            Long productId,
            Authentication authentication
    ) {

        User user =
                getAuthenticatedUser(authentication);

        Conversation conversation =
                getConversationForUser(
                        conversationId,
                        user
                );

        Organization organization =
                conversation.getOrganization();

        if (organization == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Conversation organization is required"
            );
        }

        // =====================================================
        // GET PRODUCT
        // =====================================================

        Product product =
                productRepository
                        .findById(productId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Product not found"
                                )
                        );

        // =====================================================
        // ORGANIZATION CHECK
        // =====================================================

        if (product.getOrganization() == null ||
                !product.getOrganization()
                        .getId()
                        .equals(organization.getId())) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Product belongs to another organization"
            );
        }

        // =====================================================
        // ACTIVE CHECK
        // =====================================================

        if (product.getStatus() == null ||
                !"ACTIVE".equalsIgnoreCase(
                        product.getStatus().trim()
                )) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Inactive product cannot be sent"
            );
        }

        // =====================================================
        // META RETAILER ID CHECK
        // =====================================================

        if (product.getMetaRetailerId() == null ||
                product.getMetaRetailerId().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Product is not synced with Meta Catalog"
            );
        }

        // =====================================================
        // CONTACT CHECK
        // =====================================================

        if (conversation.getContact() == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Conversation contact is required"
            );
        }

        String customerPhoneNumber =
                conversation.getContact()
                        .getPhoneNumber();

        if (customerPhoneNumber == null ||
                customerPhoneNumber.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Customer phone number is required"
            );
        }

        // =====================================================
        // SEND PRODUCT TO WHATSAPP
        // =====================================================

        String messageId =
                whatsAppCloudApiService.sendProductMessage(
                        organization.getId(),
                        customerPhoneNumber,
                        product
                );

        return messageId;
    }

    // =========================================================
    // GET CONVERSATION FOR CURRENT USER
    // =========================================================

    private Conversation getConversationForUser(
            Long conversationId,
            User user
    ) {

        if (user.getRole() == Role.SUPER_ADMIN) {

            return conversationRepository
                    .findById(conversationId)
                    .orElseThrow(() ->
                            new ResponseStatusException(
                                    HttpStatus.NOT_FOUND,
                                    "Conversation not found"
                            )
                    );
        }

        Organization organization =
                user.getOrganization();

        if (organization == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "User is not assigned to an organization"
            );
        }

        Conversation conversation =
                conversationRepository
                        .findByIdAndOrganizationId(
                                conversationId,
                                organization.getId()
                        )
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Conversation not found"
                                )
                        );

        // =====================================================
        // AGENT CHECK
        // =====================================================

        if (user.getRole() == Role.AGENT) {

            if (conversation.getAssignedAgent() == null ||
                    !conversation.getAssignedAgent()
                            .getId()
                            .equals(user.getId())) {

                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "You are not assigned to this conversation"
                );
            }
        }

        return conversation;
    }

    // =========================================================
    // GET AUTHENTICATED USER
    // =========================================================

    private User getAuthenticatedUser(
            Authentication authentication
    ) {

        if (authentication == null ||
                authentication.getName() == null) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "User is not authenticated"
            );
        }

        return userRepository
                .findByEmail(
                        authentication.getName()
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "User not found"
                        )
                );
    }
}