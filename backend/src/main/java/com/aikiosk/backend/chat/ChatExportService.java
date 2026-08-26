package com.aikiosk.backend.chat;

import com.aikiosk.backend.config.KioskProperties;
import com.aikiosk.backend.session.SessionExpiredException;
import com.aikiosk.backend.session.SessionService;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Emails a plain-text transcript of the session's conversation on request.
 * Deliberately email-only - nothing is written to disk on the kiosk terminal,
 * and the email address itself is never persisted, only used for this one
 * send, to keep the data-collection footprint minimal.
 */
@Service
public class ChatExportService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final SessionService sessionService;
    private final JavaMailSender mailSender;
    private final KioskProperties kioskProperties;

    public ChatExportService(SessionService sessionService, JavaMailSender mailSender, KioskProperties kioskProperties) {
        this.sessionService = sessionService;
        this.mailSender = mailSender;
        this.kioskProperties = kioskProperties;
    }

    public void export(ExportRequest request) {
        // Allowed while paused too (a customer who just logged out may still
        // want a copy) - only a fully expired/gone session is rejected.
        sessionService.getStatus(request.sessionId()).orElseThrow(SessionExpiredException::new);

        String email = request.email() == null ? "" : request.email().trim();
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new InvalidEmailException();
        }

        List<ChatTurn> turns = sessionService.getMessages(request.sessionId());
        String transcript = "AI Kiosk - Chat Transcript\nExported: " + Instant.now() + "\n\n"
                + ChatTranscriptFormatter.format(turns);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(kioskProperties.getEmail().getFromAddress());
        message.setTo(email);
        message.setSubject("Your AI Kiosk chat transcript");
        message.setText(transcript);

        try {
            mailSender.send(message);
        } catch (MailException e) {
            throw new EmailDeliveryException();
        }
    }
}
