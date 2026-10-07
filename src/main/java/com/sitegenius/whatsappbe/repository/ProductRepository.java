package com.sitegenius.whatsappbe.repository;

import com.sitegenius.whatsappbe.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

    Optional<Product> findByIdAndOrganizationId(Long id, Long organizationId);

    boolean existsByOrganizationIdAndSku(Long organizationId, String sku);

    Optional<Product> findByOrganizationIdAndSku(Long organizationId, String sku);

    List<Product> findByOrganizationIdAndStatusOrderByCreatedAtDesc(
            Long organizationId,
            String status
    );
}