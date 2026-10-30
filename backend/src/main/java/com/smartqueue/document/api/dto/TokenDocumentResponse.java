package com.smartqueue.document.api.dto;

import com.smartqueue.document.domain.DocumentVerificationStatus;
import com.smartqueue.document.domain.TokenDocument;

import java.time.ZonedDateTime;
import java.util.UUID;

public class TokenDocumentResponse {

    private UUID id;
    private UUID tokenId;
    private String tokenNumber;
    private String documentType;
    private String fileName;
    private String fileContentType;
    private String fileData;
    private DocumentVerificationStatus verificationStatus;
    private Double aiConfidence;
    private String aiModelVersion;
    private String extractedData;
    private String failureReason;
    private UUID reviewedByUserId;
    private String reviewedByName;
    private ZonedDateTime reviewedAt;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

    public static TokenDocumentResponse fromEntity(TokenDocument doc) {
        TokenDocumentResponse res = new TokenDocumentResponse();
        res.id = doc.getId();
        if (doc.getQueueToken() != null) {
            res.tokenId = doc.getQueueToken().getId();
            res.tokenNumber = doc.getQueueToken().getTokenNumber();
        }
        res.documentType = doc.getDocumentType();
        res.fileName = doc.getFileName();
        res.fileContentType = doc.getFileContentType();
        res.fileData = doc.getFileData();
        res.verificationStatus = doc.getVerificationStatus();
        res.aiConfidence = doc.getAiConfidence();
        res.aiModelVersion = doc.getAiModelVersion();
        res.extractedData = doc.getExtractedData();
        res.failureReason = doc.getFailureReason();
        if (doc.getReviewedByUser() != null) {
            res.reviewedByUserId = doc.getReviewedByUser().getId();
            res.reviewedByName = doc.getReviewedByUser().getFullName();
        }
        res.reviewedAt = doc.getReviewedAt();
        res.createdAt = doc.getCreatedAt();
        res.updatedAt = doc.getUpdatedAt();
        return res;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTokenId() {
        return tokenId;
    }

    public void setTokenId(UUID tokenId) {
        this.tokenId = tokenId;
    }

    public String getTokenNumber() {
        return tokenNumber;
    }

    public void setTokenNumber(String tokenNumber) {
        this.tokenNumber = tokenNumber;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileContentType() {
        return fileContentType;
    }

    public void setFileContentType(String fileContentType) {
        this.fileContentType = fileContentType;
    }

    public String getFileData() {
        return fileData;
    }

    public void setFileData(String fileData) {
        this.fileData = fileData;
    }

    public DocumentVerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(DocumentVerificationStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public Double getAiConfidence() {
        return aiConfidence;
    }

    public void setAiConfidence(Double aiConfidence) {
        this.aiConfidence = aiConfidence;
    }

    public String getAiModelVersion() {
        return aiModelVersion;
    }

    public void setAiModelVersion(String aiModelVersion) {
        this.aiModelVersion = aiModelVersion;
    }

    public String getExtractedData() {
        return extractedData;
    }

    public void setExtractedData(String extractedData) {
        this.extractedData = extractedData;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public UUID getReviewedByUserId() {
        return reviewedByUserId;
    }

    public void setReviewedByUserId(UUID reviewedByUserId) {
        this.reviewedByUserId = reviewedByUserId;
    }

    public String getReviewedByName() {
        return reviewedByName;
    }

    public void setReviewedByName(String reviewedByName) {
        this.reviewedByName = reviewedByName;
    }

    public ZonedDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(ZonedDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(ZonedDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
