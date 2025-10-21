package com.onsemi.cim.apps.exensio.exensioDearchiver.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    private final String fromAddress;

    public MailService(@Autowired(required = false) @Nullable JavaMailSender mailSender,
                       @Value("${app.mail.from:no-reply@onsemi.com}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void send(String to, String subject, String body) {
        if (mailSender == null) {
            log.info("MailSender not configured; skipping mail to {} subject={}", to, subject);
            return;
        }
        try {
            // Create a multipart MIME message with both plain text and HTML parts.
            MimeMessage mime = ((JavaMailSender) mailSender).createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, "UTF-8");
            if (fromAddress != null && !fromAddress.isBlank()) helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            // plain text always present
            String plain = body;
            // Basic HTML version: convert URLs to links by wrapping the body; keep the token visible
            String html = "<html><body><pre style=\"font-family:inherit;white-space:pre-wrap\">" + org.apache.commons.text.StringEscapeUtils.escapeHtml4(body) + "</pre>";
            // If the body contains an http(s):// URL, also render a clearer link/button for the first occurrence
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("(https?://[\\w\\-\\./?&=%#~:+,;@]+)");
            java.util.regex.Matcher m = p.matcher(body);
            if (m.find()) {
                String url = m.group(1);
                html += "<p><a href=\"" + org.apache.commons.text.StringEscapeUtils.escapeHtml4(url) + "\" style=\"display:inline-block;padding:10px 16px;background:#0b67ff;color:#fff;border-radius:6px;text-decoration:none\">Reset password</a></p>";
            }
            html += "</body></html>";
            helper.setText(plain, html);
            ((JavaMailSender) mailSender).send(mime);
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
            if (fromAddress != null && !fromAddress.isBlank()) helper.setFrom(fromAddress);
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
