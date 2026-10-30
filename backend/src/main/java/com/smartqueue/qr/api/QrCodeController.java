package com.smartqueue.qr.api;

import com.smartqueue.common.api.ApiResponse;
import com.smartqueue.qr.application.QrCodeService;
import com.smartqueue.qr.application.QrCodeService.QrCodeInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/qr")
public class QrCodeController {

    private final QrCodeService qrCodeService;

    public QrCodeController(QrCodeService qrCodeService) {
        this.qrCodeService = qrCodeService;
    }

    /** GET /api/qr/lan-ip - Dynamically discover host LAN IP for mobile QR codes */
    @GetMapping("/lan-ip")
    public ResponseEntity<ApiResponse<Map<String, String>>> getLanIp() {
        String ip = "127.0.0.1";
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && addr.isSiteLocalAddress()) {
                        ip = addr.getHostAddress();
                        break;
                    }
                }
                if (!"127.0.0.1".equals(ip)) {
                    break;
                }
            }
            if ("127.0.0.1".equals(ip)) {
                ip = InetAddress.getLocalHost().getHostAddress();
            }
        } catch (Exception ignored) {
        }
        return ResponseEntity.ok(ApiResponse.success(Map.of("ip", ip), MDC.get("correlationId")));
    }

    private String resolveBaseUrl(String baseUrl, HttpServletRequest request) {
        if (baseUrl != null && !baseUrl.isBlank()) {
            return baseUrl.trim().replaceAll("/+$", "");
        }
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank()) {
            return origin.trim().replaceAll("/+$", "");
        }
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank()) {
            try {
                java.net.URI uri = java.net.URI.create(referer);
                String scheme = uri.getScheme() != null ? uri.getScheme() : "http";
                String host = uri.getHost();
                int port = uri.getPort();
                if (host != null) {
                    return scheme + "://" + host + (port > 0 && port != 80 && port != 443 ? ":" + port : "");
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /** GET /api/qr/queue/{queueId} - Public QR code info & deep link for a queue */
    @GetMapping("/queue/{queueId}")
    public ResponseEntity<ApiResponse<QrCodeInfo>> getQueueQr(
            @PathVariable UUID queueId,
            @RequestParam(defaultValue = "300") int size,
            @RequestParam(required = false) String baseUrl,
            HttpServletRequest request) {
        String effectiveBaseUrl = resolveBaseUrl(baseUrl, request);
        QrCodeInfo info = qrCodeService.getQueueQrInfo(queueId, size, size, effectiveBaseUrl);
        return ResponseEntity.ok(ApiResponse.success(info, MDC.get("correlationId")));
    }

    /** GET /api/qr/queue/{queueId}/image - Direct PNG image stream for queue QR */
    @GetMapping(value = "/queue/{queueId}/image", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getQueueQrImage(
            @PathVariable UUID queueId,
            @RequestParam(defaultValue = "300") int size,
            @RequestParam(required = false) String baseUrl,
            HttpServletRequest request) {
        String effectiveBaseUrl = resolveBaseUrl(baseUrl, request);
        String deepLink = qrCodeService.buildQueueDeepLink(queueId, effectiveBaseUrl);
        byte[] imageBytes = qrCodeService.generateQrCodePng(deepLink, size, size);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"queue-qr-" + queueId + ".png\"")
                .contentType(MediaType.IMAGE_PNG)
                .body(imageBytes);
    }

    /** GET /api/qr/branch/{branchId} - Public QR code info for branch */
    @GetMapping("/branch/{branchId}")
    public ResponseEntity<ApiResponse<QrCodeInfo>> getBranchQr(
            @PathVariable UUID branchId,
            @RequestParam(defaultValue = "300") int size,
            @RequestParam(required = false) String baseUrl,
            HttpServletRequest request) {
        String effectiveBaseUrl = resolveBaseUrl(baseUrl, request);
        QrCodeInfo info = qrCodeService.getBranchQrInfo(branchId, size, size, effectiveBaseUrl);
        return ResponseEntity.ok(ApiResponse.success(info, MDC.get("correlationId")));
    }

    /** GET /api/qr/service/{serviceId} - Public QR code info for service */
    @GetMapping("/service/{serviceId}")
    public ResponseEntity<ApiResponse<QrCodeInfo>> getServiceQr(
            @PathVariable UUID serviceId,
            @RequestParam(defaultValue = "300") int size,
            @RequestParam(required = false) String baseUrl,
            HttpServletRequest request) {
        String effectiveBaseUrl = resolveBaseUrl(baseUrl, request);
        QrCodeInfo info = qrCodeService.getServiceQrInfo(serviceId, size, size, effectiveBaseUrl);
        return ResponseEntity.ok(ApiResponse.success(info, MDC.get("correlationId")));
    }
}
