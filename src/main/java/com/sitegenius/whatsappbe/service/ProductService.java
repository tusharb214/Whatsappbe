package com.sitegenius.whatsappbe.service;

import com.sitegenius.whatsappbe.dto.product.ProductRequest;
import com.sitegenius.whatsappbe.dto.product.ProductResponse;

import com.sitegenius.whatsappbe.entity.Category;
import com.sitegenius.whatsappbe.entity.Organization;
import com.sitegenius.whatsappbe.entity.Product;
import com.sitegenius.whatsappbe.entity.User;

import com.sitegenius.whatsappbe.enums.Role;

import com.sitegenius.whatsappbe.repository.CategoryRepository;
import com.sitegenius.whatsappbe.repository.OrganizationRepository;
import com.sitegenius.whatsappbe.repository.ProductRepository;
import com.sitegenius.whatsappbe.repository.UserRepository;
import com.sitegenius.whatsappbe.service.WhatsAppCloudApiService;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final WhatsAppCloudApiService whatsAppCloudApiService;

    public ProductService(
            ProductRepository productRepository,
            OrganizationRepository organizationRepository,
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            WhatsAppCloudApiService whatsAppCloudApiService
    ) {
        this.productRepository = productRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.whatsAppCloudApiService = whatsAppCloudApiService;
    }

    // =========================
    // CREATE PRODUCT
    // =========================

    @Transactional
    public ProductResponse createProduct(
            ProductRequest request,
            Authentication authentication
    ) {

        User user = getAuthenticatedUser(authentication);

        checkAdminOrSuperAdmin(user);

        Organization organization =
                getOrganizationForUser(user);

        String sku =
                request.sku().trim();

        if (productRepository.existsByOrganizationIdAndSku(
                organization.getId(),
                sku
        )) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Product with this SKU already exists"
            );
        }

        Product product =
                new Product();

        product.setOrganization(
                organization
        );

        // =========================
        // CATEGORY
        // =========================

        if (request.categoryId() != null) {

            Category category =
                    categoryRepository
                            .findByIdAndOrganizationId(
                                    request.categoryId(),
                                    organization.getId()
                            )
                            .orElseThrow(() ->
                                    new ResponseStatusException(
                                            HttpStatus.NOT_FOUND,
                                            "Category not found"
                                    )
                            );

            if (!"ACTIVE".equalsIgnoreCase(
                    category.getStatus()
            )) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Cannot assign inactive category"
                );
            }

            product.setCategory(
                    category
            );
        }

        product.setName(
                request.name().trim()
        );

        product.setDescription(
                request.description()
        );

        product.setPrice(
                request.price()
        );

        product.setCurrency(
                request.currency() == null ||
                        request.currency().isBlank()
                        ? "INR"
                        : request.currency()
                        .trim()
                        .toUpperCase()
        );

        product.setImageUrl(
                request.imageUrl()
        );

        product.setSku(
                sku
        );

        product.setStatus(
                request.status() == null ||
                        request.status().isBlank()
                        ? "ACTIVE"
                        : request.status()
                        .trim()
                        .toUpperCase()
        );

        Product savedProduct =
                productRepository.save(
                        product
                );
        String metaProductId =
                whatsAppCloudApiService.createMetaCatalogProduct(
                        savedProduct
                );

        savedProduct.setMetaProductId(
                metaProductId
        );

        savedProduct.setMetaRetailerId(
                savedProduct.getSku()
        );

        savedProduct =
                productRepository.save(
                        savedProduct
                );

        return toResponse(
                savedProduct
        );
    }


    // =========================
    // GET ALL PRODUCTS
    // =========================

    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts(
            Authentication authentication
    ) {

        User user =
                getAuthenticatedUser(
                        authentication
                );

        if (user.getRole() ==
                Role.SUPER_ADMIN) {

            return productRepository
                    .findAll()
                    .stream()
                    .map(this::toResponse)
                    .toList();
        }

        Organization organization =
                getOrganizationForUser(
                        user
                );

        return productRepository
                .findByOrganizationIdOrderByCreatedAtDesc(
                        organization.getId()
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }


    // =========================
    // GET PRODUCT BY ID
    // =========================

    @Transactional(readOnly = true)
    public ProductResponse getProductById(
            Long productId,
            Authentication authentication
    ) {

        User user =
                getAuthenticatedUser(
                        authentication
                );

        Product product;

        if (user.getRole() ==
                Role.SUPER_ADMIN) {

            product =
                    productRepository
                            .findById(productId)
                            .orElseThrow(() ->
                                    new ResponseStatusException(
                                            HttpStatus.NOT_FOUND,
                                            "Product not found"
                                    )
                            );

        } else {

            Organization organization =
                    getOrganizationForUser(
                            user
                    );

            product =
                    productRepository
                            .findByIdAndOrganizationId(
                                    productId,
                                    organization.getId()
                            )
                            .orElseThrow(() ->
                                    new ResponseStatusException(
                                            HttpStatus.NOT_FOUND,
                                            "Product not found"
                                    )
                            );
        }

        return toResponse(
                product
        );
    }


    // =========================
    // UPDATE PRODUCT
    // =========================

    @Transactional
    public ProductResponse updateProduct(
            Long productId,
            ProductRequest request,
            Authentication authentication
    ) {

        User user =
                getAuthenticatedUser(
                        authentication
                );

        checkAdminOrSuperAdmin(
                user
        );

        Product product;

        if (user.getRole() ==
                Role.SUPER_ADMIN) {

            product =
                    productRepository
                            .findById(productId)
                            .orElseThrow(() ->
                                    new ResponseStatusException(
                                            HttpStatus.NOT_FOUND,
                                            "Product not found"
                                    )
                            );

        } else {

            Organization organization =
                    getOrganizationForUser(
                            user
                    );

            product =
                    productRepository
                            .findByIdAndOrganizationId(
                                    productId,
                                    organization.getId()
                            )
                            .orElseThrow(() ->
                                    new ResponseStatusException(
                                            HttpStatus.NOT_FOUND,
                                            "Product not found"
                                    )
                            );
        }

        String newSku =
                request.sku().trim();

        // =========================
        // SKU
        // =========================

        if (!newSku.equals(
                product.getSku()
        )) {

            if (productRepository
                    .existsByOrganizationIdAndSku(
                            product.getOrganization().getId(),
                            newSku
                    )) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Product with this SKU already exists"
                );
            }

            product.setSku(
                    newSku
            );
        }

        // =========================
        // CATEGORY
        // =========================

        if (request.categoryId() != null) {

            Category category =
                    categoryRepository
                            .findByIdAndOrganizationId(
                                    request.categoryId(),
                                    product.getOrganization().getId()
                            )
                            .orElseThrow(() ->
                                    new ResponseStatusException(
                                            HttpStatus.NOT_FOUND,
                                            "Category not found"
                                    )
                            );

            if (!"ACTIVE".equalsIgnoreCase(
                    category.getStatus()
            )) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Cannot assign inactive category"
                );
            }

            product.setCategory(
                    category
            );

        } else {

            product.setCategory(
                    null
            );
        }

        // =========================
        // PRODUCT DETAILS
        // =========================

        product.setName(
                request.name().trim()
        );

        product.setDescription(
                request.description()
        );

        product.setPrice(
                request.price()
        );

        product.setCurrency(
                request.currency() == null ||
                        request.currency().isBlank()
                        ? "INR"
                        : request.currency()
                        .trim()
                        .toUpperCase()
        );

        product.setImageUrl(
                request.imageUrl()
        );

        product.setStatus(
                request.status() == null ||
                        request.status().isBlank()
                        ? "ACTIVE"
                        : request.status()
                        .trim()
                        .toUpperCase()
        );

        Product updatedProduct =
                productRepository.save(
                        product
                );

        return toResponse(
                updatedProduct
        );
    }


    // =========================
    // DELETE / DEACTIVATE
    // =========================

    @Transactional
    public void deactivateProduct(
            Long productId,
            Authentication authentication
    ) {

        User user =
                getAuthenticatedUser(
                        authentication
                );

        checkAdminOrSuperAdmin(
                user
        );

        Product product;

        if (user.getRole() ==
                Role.SUPER_ADMIN) {

            product =
                    productRepository
                            .findById(productId)
                            .orElseThrow(() ->
                                    new ResponseStatusException(
                                            HttpStatus.NOT_FOUND,
                                            "Product not found"
                                    )
                            );

        } else {

            Organization organization =
                    getOrganizationForUser(
                            user
                    );

            product =
                    productRepository
                            .findByIdAndOrganizationId(
                                    productId,
                                    organization.getId()
                            )
                            .orElseThrow(() ->
                                    new ResponseStatusException(
                                            HttpStatus.NOT_FOUND,
                                            "Product not found"
                                    )
                            );
        }

        product.setStatus(
                "INACTIVE"
        );

        productRepository.save(
                product
        );
    }


    // =========================
    // GET ACTIVE PRODUCTS
    // =========================

    @Transactional(readOnly = true)
    public List<ProductResponse> getActiveProducts(
            Authentication authentication
    ) {

        User user =
                getAuthenticatedUser(
                        authentication
                );

        if (user.getRole() ==
                Role.SUPER_ADMIN) {

            return productRepository
                    .findAll()
                    .stream()
                    .filter(product ->
                            product.getStatus() != null &&
                                    "ACTIVE".equalsIgnoreCase(
                                            product.getStatus().trim()
                                    )
                    )
                    .map(this::toResponse)
                    .toList();
        }

        Organization organization =
                getOrganizationForUser(
                        user
                );

        return productRepository
                .findByOrganizationIdAndStatusOrderByCreatedAtDesc(
                        organization.getId(),
                        "ACTIVE"
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }


    // =========================
    // HELPER METHODS
    // =========================

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


    private Organization getOrganizationForUser(
            User user
    ) {

        if (user.getOrganization() == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "User is not assigned to an organization"
            );
        }

        return user.getOrganization();
    }


    private void checkAdminOrSuperAdmin(
            User user
    ) {

        if (user.getRole() != Role.ADMIN &&
                user.getRole() != Role.SUPER_ADMIN) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only ADMIN or SUPER_ADMIN can manage products"
            );
        }
    }


    // =========================
    // PRODUCT → RESPONSE
    // =========================

    private ProductResponse toResponse(
            Product product
    ) {

        Long categoryId = null;
        String categoryName = null;

        if (product.getCategory() != null) {

            categoryId =
                    product.getCategory().getId();

            categoryName =
                    product.getCategory().getName();
        }

        return new ProductResponse(

                product.getId(),

                product.getOrganization()
                        .getId(),

                product.getName(),

                product.getDescription(),

                product.getPrice(),

                product.getCurrency(),

                product.getImageUrl(),

                product.getSku(),

                product.getStatus(),

                categoryId,

                categoryName,

                product.getMetaProductId(),

                product.getMetaRetailerId(),

                product.getCreatedAt(),

                product.getUpdatedAt()
        );
    }

    // =========================
// SYNC PRODUCT TO META
// =========================

    @Transactional
    public ProductResponse syncProductToMeta(
            Long productId,
            Authentication authentication
    ) {

        User user = getAuthenticatedUser(authentication);

        checkAdminOrSuperAdmin(user);

        Product product;

        if (user.getRole() == Role.SUPER_ADMIN) {

            product = productRepository
                    .findById(productId)
                    .orElseThrow(() ->
                            new ResponseStatusException(
                                    HttpStatus.NOT_FOUND,
                                    "Product not found"
                            )
                    );

        } else {

            Organization organization =
                    getOrganizationForUser(user);

            product =
                    productRepository
                            .findByIdAndOrganizationId(
                                    productId,
                                    organization.getId()
                            )
                            .orElseThrow(() ->
                                    new ResponseStatusException(
                                            HttpStatus.NOT_FOUND,
                                            "Product not found"
                                    )
                            );
        }

        if (product.getStatus() == null ||
                !"ACTIVE".equalsIgnoreCase(
                        product.getStatus().trim()
                )) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Inactive product cannot be synced"
            );
        }

        String metaProductId =
                whatsAppCloudApiService
                        .createMetaCatalogProduct(product);

        product.setMetaProductId(metaProductId);

        product.setMetaRetailerId(
                product.getSku()
        );

        Product savedProduct =
                productRepository.save(product);

        return toResponse(savedProduct);
    }
}