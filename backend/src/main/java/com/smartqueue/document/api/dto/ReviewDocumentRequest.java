package com.smartqueue.document.api.dto;

import com.smartqueue.document.domain.DocumentVerificationStatus;
import jakarta.validation.constraints.NotNull;

public class ReviewDocumentRequest {

    @NotNull(message = "Review status is required")
    private DocumentVerificationStatus status;

    private String notes;

    public DocumentVerificationStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentVerificationStatus status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
