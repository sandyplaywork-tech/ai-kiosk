package com.aikiosk.backend.auth;

import com.anthropic.errors.AnthropicServiceException;
import com.aikiosk.backend.chat.EmailDeliveryException;
import com.aikiosk.backend.chat.InvalidEmailException;
import com.aikiosk.backend.session.SessionExpiredException;
import com.aikiosk.backend.session.TokenLimitReachedException;
import com.aikiosk.backend.voucher.VoucherNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleInvalidCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid_credentials"));
    }

    @ExceptionHandler(VoucherAlreadyUsedException.class)
    public ResponseEntity<Map<String, String>> handleVoucherAlreadyUsed() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "voucher_already_used"));
    }

    @ExceptionHandler(SessionExpiredException.class)
    public ResponseEntity<Map<String, String>> handleSessionExpired() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "session_expired"));
    }

    @ExceptionHandler(TokenLimitReachedException.class)
    public ResponseEntity<Map<String, String>> handleTokenLimitReached() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "token_limit_reached"));
    }

    @ExceptionHandler(VoucherNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleVoucherNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "voucher_not_found"));
    }

    @ExceptionHandler(InvalidEmailException.class)
    public ResponseEntity<Map<String, String>> handleInvalidEmail() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid_email"));
    }

    @ExceptionHandler(EmailDeliveryException.class)
    public ResponseEntity<Map<String, String>> handleEmailDeliveryFailed() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "email_delivery_failed"));
    }

    @ExceptionHandler(AnthropicServiceException.class)
    public ResponseEntity<Map<String, String>> handleAnthropicServiceException() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "llm_upstream_error"));
    }
}
