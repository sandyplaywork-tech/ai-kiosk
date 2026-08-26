package com.aikiosk.backend.session;

import com.aikiosk.backend.voucher.Voucher;
import com.aikiosk.backend.voucher.VoucherRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminSessionService {

    private final SessionService sessionService;
    private final VoucherRepository voucherRepository;

    public AdminSessionService(SessionService sessionService, VoucherRepository voucherRepository) {
        this.sessionService = sessionService;
        this.voucherRepository = voucherRepository;
    }

    public List<AdminSessionSummary> listActiveSessions() {
        return sessionService.listActiveSessions().stream()
                .map(this::toSummary)
                .toList();
    }

    private AdminSessionSummary toSummary(SessionOverview overview) {
        String label = voucherRepository.findById(overview.voucherId())
                .map(this::labelFor)
                .orElse("(unknown voucher)");
        return new AdminSessionSummary(
                overview.sessionId(),
                label,
                overview.tokenCap(),
                overview.tokensRemaining(),
                overview.timeRemainingSeconds(),
                overview.paused());
    }

    private String labelFor(Voucher voucher) {
        if (voucher.getUsername() != null) {
            return voucher.getUsername();
        }
        if (voucher.getVoucherCode() != null) {
            return voucher.getVoucherCode();
        }
        return "(unlabeled)";
    }

    /**
     * Forced, immediate end - no grace period, unlike a customer's own
     * logout. Reuses {@link SessionService#endSession}, which was reserved
     * for exactly this since build-sequence step 6a.
     */
    public void killSession(String sessionId) {
        sessionService.endSession(sessionId);
    }
}
