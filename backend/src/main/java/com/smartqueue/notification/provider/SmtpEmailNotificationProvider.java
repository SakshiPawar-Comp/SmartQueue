package com.smartqueue.notification.provider;

import com.smartqueue.notification.domain.Notification;
import com.smartqueue.notification.domain.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Primary
public class SmtpEmailNotificationProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailNotificationProvider.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    public SmtpEmailNotificationProvider(@Autowired(required = false) JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public ProviderResult send(Notification notification) {
        String recipient = notification.getRecipient();
        if (recipient == null && notification.getUser() != null) {
            recipient = notification.getUser().getEmail();
        }

        if (recipient == null || recipient.isBlank()) {
            return ProviderResult.failed("No email recipient address");
        }

        // Fallback to log adapter if mail sender is not configured or username is blank
        if (mailSender == null || fromAddress == null || fromAddress.isBlank()) {
            log.info("[SAFE_DEV_ADAPTER] No SMTP username configured. Sending EMAIL via console log to {}: Subject='{}', Body='{}'",
                    recipient, notification.getTitle(), notification.getMessage());
            return ProviderResult.sent("email-log-" + UUID.randomUUID());
        }

        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setFrom(fromAddress);
            mailMessage.setTo(recipient);
            mailMessage.setSubject(notification.getTitle());
            mailMessage.setText(notification.getMessage());

            mailSender.send(mailMessage);
            log.info("Email delivered successfully via SMTP to {}: Subject='{}'", recipient, notification.getTitle());
            return ProviderResult.sent("smtp-" + UUID.randomUUID());
        } catch (Exception e) {
            log.error("Failed to send email to {} via SMTP: {}", recipient, e.getMessage(), e);
            return ProviderResult.failed("SMTP send failed: " + e.getMessage());
        }
    }
}
