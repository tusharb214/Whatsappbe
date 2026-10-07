package com.sitegenius.whatsappbe.service;

import com.sitegenius.whatsappbe.dto.category.CategoryRequest;
import com.sitegenius.whatsappbe.dto.category.CategoryResponse;
import com.sitegenius.whatsappbe.entity.Category;
import com.sitegenius.whatsappbe.entity.Organization;
import com.sitegenius.whatsappbe.entity.User;
import com.sitegenius.whatsappbe.enums.Role;
import com.sitegenius.whatsappbe.repository.CategoryRepository;
import com.sitegenius.whatsappbe.repository.OrganizationRepository;
import com.sitegenius.whatsappbe.repository.UserRepository;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;

    public CategoryService(
            CategoryRepository categoryRepository,
            UserRepository userRepository,
            OrganizationRepository organizationRepository
    ) {
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
    }

    // =========================================================
    // GET CURRENT USER
    // =========================================================

    private User getAuthenticatedUser() {

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

        String email = authentication.getName();

        return userRepository
                .findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Authenticated user not found"
                        ));
    }

    // =========================================================
    // GET CURRENT ROLE
    // =========================================================

    private Role getCurrentRole() {

        return getAuthenticatedUser().getRole();
    }

    // =========================================================
    // GET CURRENT ORGANIZATION ID
    // =========================================================

    private Long getCurrentOrganizationId() {

        User user = getAuthenticatedUser();

        if (user.getOrganization() == null) {
            return null;
        }

        return user.getOrganization().getId();
    }

    // =========================================================
    // CHECK ADMIN / SUPER ADMIN
    // =========================================================

    private void checkAdminOrSuperAdmin() {

        Role role = getCurrentRole();

        if (role != Role.ADMIN &&
                role != Role.SUPER_ADMIN) {

            throw new RuntimeException(
                    "Only ADMIN or SUPER_ADMIN can manage categories"
            );
        }
    }

    // =========================================================
    // GET ORGANIZATION
    // =========================================================

    private Organization getOrganizationForUser() {

        User user = getAuthenticatedUser();

        if (user.getOrganization() == null) {

            throw new RuntimeException(
                    "Organization is required"
            );
        }

        return organizationRepository
                .findById(
                        user.getOrganization().getId()
                )
                .orElseThrow(() ->
                        new RuntimeException(
                                "Organization not found"
                        ));
    }

    // =========================================================
    // CREATE CATEGORY
    // =========================================================

    @Transactional
    public CategoryResponse createCategory(
            CategoryRequest request
    ) {

        checkAdminOrSuperAdmin();

        Long organizationId =
                getCurrentOrganizationId();

        if (organizationId == null) {

            throw new RuntimeException(
                    "Organization is required"
            );
        }

        Organization organization =
                organizationRepository
                        .findById(organizationId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Organization not found"
                                ));

        String categoryName =
                request.name().trim();

        // =====================================================
        // DUPLICATE NAME CHECK
        // =====================================================

        if (categoryRepository
                .existsByOrganizationIdAndName(
                        organizationId,
                        categoryName
                )) {

            throw new RuntimeException(
                    "Category with this name already exists"
            );
        }

        Category category =
                new Category();

        category.setOrganization(
                organization
        );

        category.setName(
                categoryName
        );

        category.setDescription(
                request.description()
        );

        String status =
                request.status();

        if (status == null ||
                status.isBlank()) {

            status = "ACTIVE";
        }

        category.setStatus(
                status.trim().toUpperCase()
        );

        Category savedCategory =
                categoryRepository.save(category);

        return toResponse(savedCategory);
    }

    // =========================================================
    // GET ALL CATEGORIES
    // =========================================================

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {

        Role role = getCurrentRole();

        List<Category> categories;

        // =====================================================
        // SUPER ADMIN
        // =====================================================

        if (role == Role.SUPER_ADMIN) {

            categories =
                    categoryRepository
                            .findAll();

        }

        // =====================================================
        // ADMIN / AGENT
        // =====================================================

        else {

            Long organizationId =
                    getCurrentOrganizationId();

            if (organizationId == null) {

                throw new RuntimeException(
                        "Organization is required"
                );
            }

            categories =
                    categoryRepository
                            .findByOrganizationIdOrderByCreatedAtDesc(
                                    organizationId
                            );
        }

        return categories
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // =========================================================
    // GET ACTIVE CATEGORIES
    // =========================================================

    @Transactional(readOnly = true)
    public List<CategoryResponse> getActiveCategories() {

        Role role = getCurrentRole();

        List<Category> categories;

        // =====================================================
        // SUPER ADMIN
        // =====================================================

        if (role == Role.SUPER_ADMIN) {

            categories =
                    categoryRepository
                            .findAll()
                            .stream()
                            .filter(category ->
                                    category.getStatus() != null &&
                                            "ACTIVE".equalsIgnoreCase(
                                                    category.getStatus().trim()
                                            )
                            )
                            .toList();
        }

        // =====================================================
        // ADMIN / AGENT
        // =====================================================

        else {

            Long organizationId =
                    getCurrentOrganizationId();

            if (organizationId == null) {

                throw new RuntimeException(
                        "Organization is required"
                );
            }

            categories =
                    categoryRepository
                            .findByOrganizationIdAndStatusOrderByCreatedAtDesc(
                                    organizationId,
                                    "ACTIVE"
                            );
        }

        return categories
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // =========================================================
    // GET CATEGORY BY ID
    // =========================================================

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(
            Long categoryId
    ) {

        Role role = getCurrentRole();

        Category category;

        // =====================================================
        // SUPER ADMIN
        // =====================================================

        if (role == Role.SUPER_ADMIN) {

            category =
                    categoryRepository
                            .findById(categoryId)
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "Category not found"
                                    ));
        }

        // =====================================================
        // ADMIN / AGENT
        // =====================================================

        else {

            Long organizationId =
                    getCurrentOrganizationId();

            if (organizationId == null) {

                throw new RuntimeException(
                        "Organization is required"
                );
            }

            category =
                    categoryRepository
                            .findByIdAndOrganizationId(
                                    categoryId,
                                    organizationId
                            )
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "Category not found"
                                    ));
        }

        return toResponse(category);
    }

    // =========================================================
    // UPDATE CATEGORY
    // =========================================================

    @Transactional
    public CategoryResponse updateCategory(
            Long categoryId,
            CategoryRequest request
    ) {

        checkAdminOrSuperAdmin();

        Role role = getCurrentRole();

        Category category;

        // =====================================================
        // SUPER ADMIN
        // =====================================================

        if (role == Role.SUPER_ADMIN) {

            category =
                    categoryRepository
                            .findById(categoryId)
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "Category not found"
                                    ));
        }

        // =====================================================
        // ADMIN
        // =====================================================

        else {

            Long organizationId =
                    getCurrentOrganizationId();

            if (organizationId == null) {

                throw new RuntimeException(
                        "Organization is required"
                );
            }

            category =
                    categoryRepository
                            .findByIdAndOrganizationId(
                                    categoryId,
                                    organizationId
                            )
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "Category not found"
                                    ));
        }

        String newName =
                request.name().trim();

        // =====================================================
        // DUPLICATE NAME CHECK
        // =====================================================

        if (!category.getName()
                .equalsIgnoreCase(newName)) {

            Long organizationId =
                    category.getOrganization()
                            .getId();

            boolean exists =
                    categoryRepository
                            .existsByOrganizationIdAndName(
                                    organizationId,
                                    newName
                            );

            if (exists) {

                throw new RuntimeException(
                        "Category with this name already exists"
                );
            }
        }

        category.setName(
                newName
        );

        category.setDescription(
                request.description()
        );

        if (request.status() != null &&
                !request.status().isBlank()) {

            category.setStatus(
                    request.status()
                            .trim()
                            .toUpperCase()
            );
        }

        Category updatedCategory =
                categoryRepository.save(category);

        return toResponse(updatedCategory);
    }

    // =========================================================
    // DEACTIVATE CATEGORY
    // =========================================================

    @Transactional
    public void deactivateCategory(
            Long categoryId
    ) {

        checkAdminOrSuperAdmin();

        Role role = getCurrentRole();

        Category category;

        // =====================================================
        // SUPER ADMIN
        // =====================================================

        if (role == Role.SUPER_ADMIN) {

            category =
                    categoryRepository
                            .findById(categoryId)
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "Category not found"
                                    ));
        }

        // =====================================================
        // ADMIN
        // =====================================================

        else {

            Long organizationId =
                    getCurrentOrganizationId();

            if (organizationId == null) {

                throw new RuntimeException(
                        "Organization is required"
                );
            }

            category =
                    categoryRepository
                            .findByIdAndOrganizationId(
                                    categoryId,
                                    organizationId
                            )
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "Category not found"
                                    ));
        }

        category.setStatus(
                "INACTIVE"
        );

        categoryRepository.save(
                category
        );
    }

    // =========================================================
    // ENTITY → RESPONSE
    // =========================================================

    private CategoryResponse toResponse(
            Category category
    ) {

        return new CategoryResponse(

                category.getId(),

                category.getOrganization()
                        .getId(),

                category.getName(),

                category.getDescription(),

                category.getStatus(),

                category.getCreatedAt(),

                category.getUpdatedAt()
        );
    }
}