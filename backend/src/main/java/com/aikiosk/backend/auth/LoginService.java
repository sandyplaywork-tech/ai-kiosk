package com.aikiosk.backend.auth;

import com.aikiosk.backend.auth.dto.LoginRequest;
import com.aikiosk.backend.auth.dto.LoginResponse;
import com.aikiosk.backend.session.SessionService;
import com.aikiosk.backend.session.SessionStatus;
import com.aikiosk.backend.voucher.Voucher;
import com.aikiosk.backend.voucher.VoucherRepository;
import com.aikiosk.backend.voucher.VoucherStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class LoginService {

    private final VoucherRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;

    public LoginService(VoucherRepository repository, PasswordEncoder passwordEncoder, SessionService sessionService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Voucher voucher = resolveVoucher(request);

        // Compare-and-set instead of an entity-status check + save: guards
        // against two concurrent requests both passing a stale in-memory
        // status read and both redeeming the same single-use voucher.
        int activated = repository.updateStatusIfCurrentStatus(
                voucher.getId(), VoucherStatus.ISSUED, VoucherStatus.ACTIVE);

        if (activated == 1) {
            String sessionId = UUID.randomUUID().toString();
            sessionService.createSession(sessionId, voucher.getId(), voucher.getTokenCap(), voucher.getSessionLengthMinutes());
            return new LoginResponse(sessionId, voucher.getTokenCap(), voucher.getTokenCap(),
                    voucher.getSessionLengthMinutes() * 60L);
        }

        // Already ACTIVE - not a fresh login. The only legitimate reason that's
        // still allowed through is resuming a session the customer logged out
        // of within its grace period; anything else is a genuine reuse attempt.
        return sessionService.findResumableSessionId(voucher.getId().toString())
                .map(this::resume)
                .orElseThrow(VoucherAlreadyUsedException::new);
    }

    private LoginResponse resume(String sessionId) {
        sessionService.resumeSession(sessionId);
        SessionStatus status = sessionService.getStatus(sessionId).orElseThrow(VoucherAlreadyUsedException::new);
        return new LoginResponse(sessionId, status.tokenCap(), status.tokensRemaining(), status.timeRemainingSeconds());
    }

    private Voucher resolveVoucher(LoginRequest request) {
        if (request.voucherCode() != null && !request.voucherCode().isBlank()) {
            return repository.findByVoucherCode(request.voucherCode())
                    .orElseThrow(InvalidCredentialsException::new);
        }

        if (request.username() != null && request.password() != null) {
            Voucher voucher = repository.findByUsername(request.username())
                    .orElseThrow(InvalidCredentialsException::new);
            if (voucher.getPasswordHash() == null
                    || !passwordEncoder.matches(request.password(), voucher.getPasswordHash())) {
                throw new InvalidCredentialsException();
            }
            return voucher;
        }

        throw new InvalidCredentialsException();
    }
}
