package com.sitegenius.whatsappbe.repository;

import com.sitegenius.whatsappbe.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByOrganizationIdOrderByCreatedAtDesc(
            Long organizationId
    );

    Optional<Category> findByIdAndOrganizationId(
            Long id,
            Long organizationId
    );

    boolean existsByOrganizationIdAndName(
            Long organizationId,
            String name
    );

    Optional<Category> findByOrganizationIdAndName(
            Long organizationId,
            String name
    );

    List<Category> findByOrganizationIdAndStatusOrderByCreatedAtDesc(
            Long organizationId,
            String status
    );
}