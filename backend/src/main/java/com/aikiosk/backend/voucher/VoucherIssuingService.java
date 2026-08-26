package com.aikiosk.backend.voucher;

import com.aikiosk.backend.config.KioskProperties;
import com.aikiosk.backend.voucher.dto.IssueCredentialResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;

@Service
public class VoucherIssuingService {

    // Excludes visually ambiguous characters (0/O, 1/I/L).
    private static final String CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final String PASSWORD_CHARS = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final String USERNAME_PREFIX = "guest";

    private final VoucherRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final KioskProperties kioskProperties;
    private final SecureRandom random = new SecureRandom();

    public VoucherIssuingService(
            VoucherRepository repository,
            PasswordEncoder passwordEncoder,
            KioskProperties kioskProperties) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.kioskProperties = kioskProperties;
    }

    @Transactional
    public IssueCredentialResponse issueUsernamePassword(Integer tokenCapOverride, Integer sessionLengthOverride) {
        String username;
        do {
            username = USERNAME_PREFIX + randomDigits(6);
        } while (repository.findByUsername(username).isPresent());

        String plaintextPassword = randomString(PASSWORD_CHARS, 10);

        Voucher voucher = new Voucher();
        voucher.setUsername(username);
        voucher.setPasswordHash(passwordEncoder.encode(plaintextPassword));
        voucher.setTokenCap(resolveTokenCap(tokenCapOverride));
        voucher.setSessionLengthMinutes(resolveSessionLength(sessionLengthOverride));
        voucher.setStatus(VoucherStatus.ISSUED);
        voucher.setCreatedAt(Instant.now());
        Voucher saved = repository.save(voucher);

        return new IssueCredentialResponse(
                saved.getId(), username, plaintextPassword, null,
                saved.getTokenCap(), saved.getSessionLengthMinutes());
    }

    @Transactional
    public IssueCredentialResponse issueVoucherCode(Integer tokenCapOverride, Integer sessionLengthOverride) {
        String code;
        do {
            code = randomString(CODE_CHARS, 4) + "-" + randomString(CODE_CHARS, 4) + "-" + randomString(CODE_CHARS, 4);
        } while (repository.findByVoucherCode(code).isPresent());

        Voucher voucher = new Voucher();
        voucher.setVoucherCode(code);
        voucher.setTokenCap(resolveTokenCap(tokenCapOverride));
        voucher.setSessionLengthMinutes(resolveSessionLength(sessionLengthOverride));
        voucher.setStatus(VoucherStatus.ISSUED);
        voucher.setCreatedAt(Instant.now());
        Voucher saved = repository.save(voucher);

        return new IssueCredentialResponse(
                saved.getId(), null, null, code,
                saved.getTokenCap(), saved.getSessionLengthMinutes());
    }

    private int resolveTokenCap(Integer override) {
        return override != null ? override : kioskProperties.getSession().getTokenCap();
    }

    private int resolveSessionLength(Integer override) {
        return override != null ? override : kioskProperties.getSession().getLengthMinutes();
    }

    private String randomDigits(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    private String randomString(String alphabet, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
