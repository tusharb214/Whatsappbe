package com.sitegenius.whatsappbe.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "flow_edges",
        indexes = {
                @Index(name = "idx_flow_edge_flow", columnList = "flow_id"),
                @Index(name = "idx_flow_edge_source", columnList = "source_node_id"),
                @Index(name = "idx_flow_edge_target", columnList = "target_node_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlowEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flow_id", nullable = false)
    private Long flowId;

    @Column(name = "source_node_id", nullable = false)
    private Long sourceNodeId;

    @Column(name = "target_node_id", nullable = false)
    private Long targetNodeId;

    /**
     * Identifies which output/handle of the source node
     * this connection belongs to.
     *
     * Example:
     * "menu"
     * "subscription"
     * "yes"
     * "no"
     */
    @Column(name = "source_handle", length = 100)
    private String sourceHandle;

    /**
     * Optional condition for this connection.
     *
     * Example:
     * "payment_success"
     * "payment_failed"
     * "cart_empty"
     */
    @Column(name = "edge_condition", length = 500)
    private String condition;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}