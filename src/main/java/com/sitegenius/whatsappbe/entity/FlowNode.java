package com.sitegenius.whatsappbe.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "flow_nodes",
        indexes = {
                @Index(name = "idx_flow_node_flow", columnList = "flow_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlowNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flow_id", nullable = false)
    private Long flowId;

    @Column(name = "node_key", nullable = false, length = 100)
    private String nodeKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false, length = 50)
    private NodeType nodeType;

    @Column(nullable = false, length = 150)
    private String name;

    /**
     * Dynamic node configuration.
     *
     * Example:
     * {
     *   "text": "Welcome!",
     *   "buttons": [...]
     * }
     */
    @Column(columnDefinition = "LONGTEXT")
    private String config;

    @Column(name = "position_x")
    private Double positionX;

    @Column(name = "position_y")
    private Double positionY;

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

    public enum NodeType {

        START,

        MESSAGE,
        IMAGE,
        VIDEO,
        DOCUMENT,

        BUTTON,
        LIST,
        INPUT,

        PRODUCT,
        PRODUCT_LIST,
        CATALOG,

        LOCATION,
        CONTACT,

        CONDITION,
        SWITCH,
        DELAY,

        API_REQUEST,
        WEBHOOK,

        ASSIGN_AGENT,
        CREATE_ORDER,
        UPDATE_ORDER,

        PAYMENT,

        // Added to match the frontend node library
        TEXT_MESSAGE,
        AUDIO,
        KEYWORD_TRIGGER,
        MESSAGE_TRIGGER,
        TEXT_INPUT,
        PHONE_INPUT,
        EMAIL_INPUT,
        NUMBER_INPUT,
        DATE_INPUT,
        SET_VARIABLE,
        HTTP_REQUEST,
        ADD_TAG,
        REMOVE_TAG,
        CREATE_CONTACT,
        JUMP_TO_FLOW,
        END
    }
}