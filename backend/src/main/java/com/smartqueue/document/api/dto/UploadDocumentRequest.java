package com.smartqueue.document.api.dto;

import jakarta.validation.constraints.NotBlank;

public class UploadDocumentRequest {

    private String documentType = "GOVERNMENT_ID";

    @NotBlank(message = "File name is required")
    private String fileName;

    private String fileContentType;

    @NotBlank(message = "File data (base64) is required")
    private String fileData;

    public String getDocumentType() {
        return documentType != null ? documentType : "GOVERNMENT_ID";
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
}
