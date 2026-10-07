package com.sitegenius.whatsappbe.dto.message;

public class SendMessageRequest {

    private String messageText;

    private String messageType;

    private String mediaId;

    private String mediaUrl;

    private String mediaMimeType;

    private String mediaFileName;

    private Long mediaSize;

    private String mediaCaption;


    // =====================================================
    // GETTERS
    // =====================================================

    public String getMessageText() {
        return messageText;
    }

    public String getMessageType() {
        return messageType;
    }

    public String getMediaId() {
        return mediaId;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public String getMediaMimeType() {
        return mediaMimeType;
    }

    public String getMediaFileName() {
        return mediaFileName;
    }

    public Long getMediaSize() {
        return mediaSize;
    }

    public String getMediaCaption() {
        return mediaCaption;
    }


    // =====================================================
    // SETTERS
    // =====================================================

    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public void setMediaId(String mediaId) {
        this.mediaId = mediaId;
    }

    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }

    public void setMediaMimeType(String mediaMimeType) {
        this.mediaMimeType = mediaMimeType;
    }

    public void setMediaFileName(String mediaFileName) {
        this.mediaFileName = mediaFileName;
    }

    public void setMediaSize(Long mediaSize) {
        this.mediaSize = mediaSize;
    }

    public void setMediaCaption(String mediaCaption) {
        this.mediaCaption = mediaCaption;
    }
}