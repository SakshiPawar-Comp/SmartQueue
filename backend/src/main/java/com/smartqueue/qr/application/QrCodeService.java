package com.smartqueue.qr.application;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.smartqueue.branch.domain.Branch;
import com.smartqueue.branch.infrastructure.BranchRepository;
import com.smartqueue.queue.application.QueueNotFoundException;
import com.smartqueue.queue.domain.Queue;
import com.smartqueue.queue.infrastructure.QueueRepository;
import com.smartqueue.service.domain.Service;
import com.smartqueue.service.infrastructure.ServiceRepository;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

@Component
public class QrCodeService {

    private final QueueRepository queueRepository;
    private final BranchRepository branchRepository;
    private final ServiceRepository serviceRepository;

    public QrCodeService(
            QueueRepository queueRepository,
            BranchRepository branchRepository,
            ServiceRepository serviceRepository
    ) {
        this.queueRepository = queueRepository;
        this.branchRepository = branchRepository;
        this.serviceRepository = serviceRepository;
    }

    public record QrCodeInfo(
            String deepLinkUrl,
            String qrDataUri,
            String targetType,
            UUID targetId,
            String targetName
    ) {}

    public String buildQueueDeepLink(UUID queueId) {
        return buildQueueDeepLink(queueId, null);
    }

    public String buildQueueDeepLink(UUID queueId, String baseUrl) {
        String base = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.replaceAll("/+$", "") : "";
        return base + "/join?queueId=" + queueId;
    }

    public String buildBranchDeepLink(UUID branchId) {
        return buildBranchDeepLink(branchId, null);
    }

    public String buildBranchDeepLink(UUID branchId, String baseUrl) {
        String base = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.replaceAll("/+$", "") : "";
        return base + "/join?branchId=" + branchId;
    }

    public String buildServiceDeepLink(UUID serviceId) {
        return buildServiceDeepLink(serviceId, null);
    }

    public String buildServiceDeepLink(UUID serviceId, String baseUrl) {
        String base = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.replaceAll("/+$", "") : "";
        return base + "/join?serviceId=" + serviceId;
    }

    public byte[] generateQrCodePng(String content, int width, int height) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
            hints.put(EncodeHintType.MARGIN, 2);

            BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, width, height, hints);
            ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
            return pngOutputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate QR code: " + e.getMessage(), e);
        }
    }

    public String generateQrCodeDataUri(String content, int width, int height) {
        byte[] pngData = generateQrCodePng(content, width, height);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(pngData);
    }

    public QrCodeInfo getQueueQrInfo(UUID queueId, int width, int height) {
        return getQueueQrInfo(queueId, width, height, null);
    }

    public QrCodeInfo getQueueQrInfo(UUID queueId, int width, int height, String baseUrl) {
        Queue queue = queueRepository.findById(queueId);
        if (queue == null) {
            throw new QueueNotFoundException("Queue not found: " + queueId);
        }

        String deepLink = buildQueueDeepLink(queueId, baseUrl);
        String dataUri = generateQrCodeDataUri(deepLink, width, height);
        return new QrCodeInfo(deepLink, dataUri, "QUEUE", queue.getId(), queue.getName());
    }

    public QrCodeInfo getBranchQrInfo(UUID branchId, int width, int height) {
        return getBranchQrInfo(branchId, width, height, null);
    }

    public QrCodeInfo getBranchQrInfo(UUID branchId, int width, int height, String baseUrl) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new IllegalArgumentException("Branch not found: " + branchId));

        String deepLink = buildBranchDeepLink(branchId, baseUrl);
        String dataUri = generateQrCodeDataUri(deepLink, width, height);
        return new QrCodeInfo(deepLink, dataUri, "BRANCH", branch.getId(), branch.getName());
    }

    public QrCodeInfo getServiceQrInfo(UUID serviceId, int width, int height) {
        return getServiceQrInfo(serviceId, width, height, null);
    }

    public QrCodeInfo getServiceQrInfo(UUID serviceId, int width, int height, String baseUrl) {
        Service service = serviceRepository.findById(serviceId);
        if (service == null) {
            throw new IllegalArgumentException("Service not found: " + serviceId);
        }

        String deepLink = buildServiceDeepLink(serviceId, baseUrl);
        String dataUri = generateQrCodeDataUri(deepLink, width, height);
        return new QrCodeInfo(deepLink, dataUri, "SERVICE", service.getId(), service.getName());
    }
}
