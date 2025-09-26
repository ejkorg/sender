package com.example.reloader.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.internet.MimeMessage;
import org.springframework.stereotype.Service;

@Service
public class MailService {
    private final Logger log = LoggerFactory.getLogger(MailService.class);
    private final JavaMailSender mailSender;

    public MailService(@Autowired(required = false) @Nullable JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void send(String to, String subject, String body) {
        if (mailSender == null) {
            log.info("MailSender not configured; skipping mail to {} subject={}", to, subject);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            log.info("Sent mail to {} subject={}", to, subject);
        } catch (Exception ex) {
            log.warn("Failed to send mail to {}: {}", to, ex.getMessage());
        }
    }

    /**
     * Send mail with a single file attachment. If Mime support isn't available the body-only fallback will be tried.
     */
    public void sendWithAttachment(String to, String subject, String body, java.nio.file.Path attachmentPath) {
        if (mailSender == null) {
            log.info("MailSender not configured; skipping mail to {} subject={}", to, subject);
            return;
        }
        try {
            MimeMessage mime = ((JavaMailSender) mailSender).createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            if (attachmentPath != null && java.nio.file.Files.exists(attachmentPath)) {
                helper.addAttachment(attachmentPath.getFileName().toString(), attachmentPath.toFile());
            }
            ((JavaMailSender) mailSender).send(mime);
            log.info("Sent mail with attachment to {} subject={}", to, subject);
        } catch (Exception ex) {
            log.warn("Failed to send mail with attachment to {}: {}", to, ex.getMessage());
            // try simple send as fallback
            send(to, subject, body + "\n\n(attachment failed: " + ex.getMessage() + ")");
        }
    }
}
