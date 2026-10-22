package com.sitegenius.whatsappbe.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "flow_execution_sessions",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_flow_session_org_customer",
        columnNames = {"organization_id", "customer_phone_number"}
    ),
    indexes = {
        @Index(name = "idx_flow_session_flow", columnList = "flow_id"),
        @Index(name = "idx_flow_session_pending_node", columnList = "pending_node_id")
    }
)
public class FlowExecutionSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "customer_phone_number", nullable = false, length = 30)
    private String customerPhoneNumber;

    @Column(name = "flow_id", nullable = false)
    private Long flowId;

    @Column(name = "pending_node_id")
    private Long pendingNodeId;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void updateTimestamp() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long value) { organizationId = value; }
    public String getCustomerPhoneNumber() { return customerPhoneNumber; }
    public void setCustomerPhoneNumber(String value) { customerPhoneNumber = value; }
    public Long getFlowId() { return flowId; }
    public void setFlowId(Long value) { flowId = value; }
    public Long getPendingNodeId() { return pendingNodeId; }
    public void setPendingNodeId(Long value) { pendingNodeId = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
