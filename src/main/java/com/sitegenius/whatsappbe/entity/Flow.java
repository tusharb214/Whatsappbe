package com.sitegenius.whatsappbe.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "flows",
        indexes = {
                @Index(name = "idx_flow_organization", columnList = "organization_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Flow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "meta_flow_id", length = 100)
    private String metaFlowId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private FlowStatus status = FlowStatus.DRAFT;

    @Column(name = "meta_flow_status", length = 50)
    private String metaFlowStatus;

    @Column(name = "flow_json", columnDefinition = "LONGTEXT")
    private String flowJson;

    @Column(name = "meta_validation_errors", columnDefinition = "TEXT")
    private String metaValidationErrors;

    @Column(name = "last_published_at")
    private LocalDateTime lastPublishedAt;

    @Column(name = "last_publish_error", columnDefinition = "TEXT")
    private String lastPublishError;

    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum FlowStatus {
        DRAFT,
        PUBLISHED,
        DISABLED
    }
}