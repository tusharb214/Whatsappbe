package com.sitegenius.whatsappbe.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // =========================================================
    // ORGANIZATION
    // =========================================================

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;


    // =========================================================
    // CONVERSATION
    // =========================================================

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;


    // =========================================================
    // MESSAGE DIRECTION
    // =========================================================
    /*
     * INCOMING
     * OUTGOING
     */

    @Column(nullable = false, length = 20)
    private String direction;


    // =========================================================
    // MESSAGE TYPE
    // =========================================================
    /*
     * TEXT
     * IMAGE
     * VIDEO
     * AUDIO
     * DOCUMENT
     */

    @Column(nullable = false, length = 30)
    private String messageType;


    // =========================================================
    // MESSAGE TEXT / CAPTION
    // =========================================================
    /*
     * TEXT message:
     *     actual message text
     *
     * IMAGE:
     *     image caption
     *
     * DOCUMENT:
     *     document caption
     *
     * VIDEO:
     *     video caption
     */

    @Column(columnDefinition = "TEXT")
    private String messageText;


    // =========================================================
    // MEDIA ID
    // =========================================================
    /*
     * WhatsApp / Meta media ID.
     *
     * Example:
     * image media ID
     * document media ID
     * audio media ID
     * video media ID
     */

    @Column(name = "media_id", length = 255)
    private String mediaId;


    // =========================================================
    // MEDIA URL
    // =========================================================
    /*
     * Internal / stored media URL.
     *
     * We should NOT expose temporary Meta URLs directly
     * to the frontend.
     */

    @Column(name = "media_url", length = 1000)
    private String mediaUrl;


    // =========================================================
    // MEDIA MIME TYPE
    // =========================================================
    /*
     * image/jpeg
     * image/png
     * application/pdf
     * video/mp4
     * audio/mpeg
     * etc.
     */

    @Column(name = "media_mime_type", length = 150)
    private String mediaMimeType;


    // =========================================================
    // MEDIA FILE NAME
    // =========================================================

    @Column(name = "media_file_name", length = 255)
    private String mediaFileName;


    // =========================================================
    // MEDIA SIZE
    // =========================================================

    @Column(name = "media_size")
    private Long mediaSize;


    // =========================================================
    // MEDIA CAPTION
    // =========================================================

    @Column(name = "media_caption", columnDefinition = "TEXT")
    private String mediaCaption;


    // =========================================================
// SENDER AGENT
// =========================================================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_agent_id")
    private User senderAgent;


    // =========================================================
    // WHATSAPP MESSAGE ID
    // =========================================================

    @Column(name = "whatsapp_message_id", length = 255)
    private String whatsappMessageId;


    // =========================================================
    // WHATSAPP DELIVERY STATUS
    // =========================================================
    /*
     * SENDING
     * SENT
     * DELIVERED
     * READ
     * FAILED
     *
     * For incoming messages:
     * RECEIVED
     */

    @Column(name = "delivery_status", length = 30)
    private String deliveryStatus;


    // =========================================================
    // LOCAL READ STATUS
    // =========================================================
    /*
     * This is NOT WhatsApp blue tick.
     *
     * This is used by our Inbox to know whether the
     * agent has viewed the incoming message.
     */

    @Column(name = "is_read", nullable = false)
    private boolean read = false;


    // =========================================================
    // CREATED AT
    // =========================================================

    @Column(nullable = false)
    private LocalDateTime createdAt;


    // =========================================================
    // UPDATED AT
    // =========================================================

    @Column
    private LocalDateTime updatedAt;


    // =========================================================
    // PRE PERSIST
    // =========================================================

    @PrePersist
    protected void onCreate() {

        LocalDateTime now =
                LocalDateTime.now();

        createdAt = now;

        updatedAt = now;

        /*
         * Default delivery status.
         *
         * Incoming messages are received.
         * Outgoing messages start as SENT.
         */

        if (deliveryStatus == null ||
                deliveryStatus.isBlank()) {

            if ("INCOMING".equalsIgnoreCase(direction)) {

                deliveryStatus = "RECEIVED";

            } else {

                deliveryStatus = "SENT";
            }
        }
    }


    // =========================================================
    // PRE UPDATE
    // =========================================================

    @PreUpdate
    protected void onUpdate() {

        updatedAt =
                LocalDateTime.now();
    }


    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    public Message() {
    }


    // =========================================================
    // GETTERS / SETTERS
    // =========================================================

    public Long getId() {
        return id;
    }


    public Organization getOrganization() {
        return organization;
    }


    public void setOrganization(
            Organization organization) {

        this.organization = organization;
    }


    public Conversation getConversation() {
        return conversation;
    }


    public void setConversation(
            Conversation conversation) {

        this.conversation = conversation;
    }


    public String getDirection() {
        return direction;
    }


    public void setDirection(
            String direction) {

        this.direction = direction;
    }


    public String getMessageType() {
        return messageType;
    }


    public void setMessageType(
            String messageType) {

        this.messageType = messageType;
    }


    public String getMessageText() {
        return messageText;
    }


    public void setMessageText(
            String messageText) {

        this.messageText = messageText;
    }


    public String getMediaId() {
        return mediaId;
    }


    public void setMediaId(
            String mediaId) {

        this.mediaId = mediaId;
    }


    public String getMediaUrl() {
        return mediaUrl;
    }


    public void setMediaUrl(
            String mediaUrl) {

        this.mediaUrl = mediaUrl;
    }


    public String getMediaMimeType() {
        return mediaMimeType;
    }


    public void setMediaMimeType(
            String mediaMimeType) {

        this.mediaMimeType = mediaMimeType;
    }


    public String getMediaFileName() {
        return mediaFileName;
    }


    public void setMediaFileName(
            String mediaFileName) {

        this.mediaFileName = mediaFileName;
    }


    public Long getMediaSize() {
        return mediaSize;
    }


    public void setMediaSize(
            Long mediaSize) {

        this.mediaSize = mediaSize;
    }


    public String getMediaCaption() {
        return mediaCaption;
    }


    public void setMediaCaption(
            String mediaCaption) {

        this.mediaCaption = mediaCaption;
    }

    public User getSenderAgent() {
        return senderAgent;
    }

    public void setSenderAgent(User senderAgent) {
        this.senderAgent = senderAgent;
    }

    public String getWhatsappMessageId() {
        return whatsappMessageId;
    }


    public void setWhatsappMessageId(
            String whatsappMessageId) {

        this.whatsappMessageId =
                whatsappMessageId;
    }


    public String getDeliveryStatus() {
        return deliveryStatus;
    }


    public void setDeliveryStatus(
            String deliveryStatus) {

        this.deliveryStatus =
                deliveryStatus;
    }


    public boolean isRead() {
        return read;
    }


    public void setRead(boolean read) {
        this.read = read;
    }


    public LocalDateTime getCreatedAt() {
        return createdAt;
    }


    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}