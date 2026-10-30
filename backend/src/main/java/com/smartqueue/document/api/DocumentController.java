package com.smartqueue.document.api;

import com.smartqueue.common.api.ApiResponse;
import com.smartqueue.common.security.CustomUserDetails;
import com.smartqueue.document.api.dto.ReviewDocumentRequest;
import com.smartqueue.document.api.dto.TokenDocumentResponse;
import com.smartqueue.document.api.dto.UploadDocumentRequest;
import com.smartqueue.document.application.DocumentVerificationService;
import com.smartqueue.document.domain.TokenDocument;
import com.smartqueue.queue.domain.QueueToken;
import com.smartqueue.queue.infrastructure.QueueTokenRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentVerificationService documentVerificationService;
    private final QueueTokenRepository queueTokenRepository;

    public DocumentController(
            DocumentVerificationService documentVerificationService,
            QueueTokenRepository queueTokenRepository) {
        this.documentVerificationService = documentVerificationService;
        this.queueTokenRepository = queueTokenRepository;
    }

    /**
     * POST /api/documents/tokens/{tokenId}/upload
     * Upload and verify a document for a token using the AI service.
     */
    @PostMapping("/tokens/{tokenId}/upload")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF', 'ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<TokenDocumentResponse>> uploadAndVerify(
            @PathVariable UUID tokenId,
            @Valid @RequestBody UploadDocumentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        TokenDocument doc = documentVerificationService.uploadAndVerify(
                tokenId,
                request.getDocumentType(),
                request.getFileName(),
                request.getFileContentType(),
                request.getFileData(),
                userDetails != null ? userDetails.getUser() : null
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(TokenDocumentResponse.fromEntity(doc), "Document uploaded and processed by AI verification"));
    }

    /**
     * GET /api/documents/tokens/{tokenId}
     * Retrieve all uploaded documents and verification records for a token.
     */
    @GetMapping("/tokens/{tokenId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF', 'ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<TokenDocumentResponse>>> getDocumentsForToken(
            @PathVariable UUID tokenId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        List<TokenDocument> docs = documentVerificationService.getDocumentsForToken(tokenId);
        List<TokenDocumentResponse> responses = docs.stream().map(TokenDocumentResponse::fromEntity).toList();

        return ResponseEntity.ok(ApiResponse.success(responses, null));
    }

    /**
     * POST /api/documents/{documentId}/review
     * Staff or Admin manually approves or overrides a document status.
     */
    @PostMapping("/{documentId}/review")
    @PreAuthorize("hasAnyRole('STAFF', 'ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<TokenDocumentResponse>> reviewDocument(
            @PathVariable UUID documentId,
            @Valid @RequestBody ReviewDocumentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        TokenDocument doc = documentVerificationService.reviewDocument(
                documentId,
                request.getStatus(),
                request.getNotes(),
                userDetails != null ? userDetails.getUser() : null
        );

        return ResponseEntity.ok(ApiResponse.success(TokenDocumentResponse.fromEntity(doc), "Document review status updated"));
    }

    /**
     * GET /api/documents/tokens/{tokenId}/status
     * Check if a token has satisfied the document requirements to proceed to service.
     */
    @GetMapping("/tokens/{tokenId}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getVerificationStatus(
            @PathVariable UUID tokenId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        QueueToken token = queueTokenRepository.findById(tokenId);
        if (token == null) {
            throw new IllegalArgumentException("Token not found: " + tokenId);
        }

        boolean eligible = documentVerificationService.isTokenEligibleForService(token);
        String aggregateStatus = documentVerificationService.getAggregateStatus(token);
        List<TokenDocument> docs = documentVerificationService.getDocumentsForToken(tokenId);

        Map<String, Object> result = new HashMap<>();
        result.put("tokenId", tokenId);
        result.put("tokenNumber", token.getTokenNumber());
        result.put("isEligibleForService", eligible);
        result.put("status", aggregateStatus);
        result.put("documentCount", docs.size());
        result.put("requiresVerification", Boolean.TRUE.equals(token.getQueue().getRequiresDocumentVerification()));

        return ResponseEntity.ok(ApiResponse.success(result, null));
    }
}
