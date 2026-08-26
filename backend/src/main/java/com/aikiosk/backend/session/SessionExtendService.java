package com.aikiosk.backend.session;

import com.aikiosk.backend.auth.VoucherAlreadyUsedException;
import com.aikiosk.backend.voucher.Voucher;
import com.aikiosk.backend.voucher.VoucherNotFoundException;
import com.aikiosk.backend.voucher.VoucherRepository;
import com.aikiosk.backend.voucher.VoucherStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionExtendService {

    private final VoucherRepository voucherRepository;
    private final SessionService sessionService;

    public SessionExtendService(VoucherRepository voucherRepository, SessionService sessionService) {
        this.voucherRepository = voucherRepository;
        this.sessionService = sessionService;
    }

    @Transactional
    public ExtendResponse extend(ExtendRequest request) {
        // A paused (logged-out) session can't be extended - the customer has
        // to resume it first, same as chat.
        sessionService.getStatus(request.sessionId())
                .filter(status -> !sessionService.isPaused(request.sessionId()))
                .orElseThrow(SessionExpiredException::new);

        Voucher voucher = voucherRepository.findByVoucherCode(request.voucherCode())
                .orElseThrow(VoucherNotFoundException::new);

        int consumed = voucherRepository.updateStatusIfCurrentStatus(
                voucher.getId(), VoucherStatus.ISSUED, VoucherStatus.ACTIVE);
        if (consumed == 0) {
            throw new VoucherAlreadyUsedException();
        }

        sessionService.extendSession(request.sessionId(), voucher.getTokenCap(), voucher.getSessionLengthMinutes());

        SessionStatus status = sessionService.getStatus(request.sessionId()).orElseThrow(SessionExpiredException::new);
        return new ExtendResponse(status.tokenCap(), status.tokensRemaining(), status.timeRemainingSeconds());
    }
}
