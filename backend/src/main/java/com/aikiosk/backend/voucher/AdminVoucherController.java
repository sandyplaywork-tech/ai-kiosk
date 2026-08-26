package com.aikiosk.backend.voucher;

import com.aikiosk.backend.voucher.dto.IssueCredentialRequest;
import com.aikiosk.backend.voucher.dto.IssueCredentialResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff-facing. Guarded by AdminApiKeyFilter (X-Admin-Key header) - there is
 * no staff account system yet, that lands with the admin dashboard (step 7).
 */
@RestController
@RequestMapping("/api/admin/vouchers")
public class AdminVoucherController {

    private final VoucherIssuingService voucherIssuingService;

    public AdminVoucherController(VoucherIssuingService voucherIssuingService) {
        this.voucherIssuingService = voucherIssuingService;
    }

    @PostMapping
    public ResponseEntity<IssueCredentialResponse> issue(@RequestBody IssueCredentialRequest request) {
        IssueCredentialResponse response = switch (request.type()) {
            case USERNAME_PASSWORD ->
                    voucherIssuingService.issueUsernamePassword(request.tokenCap(), request.sessionLengthMinutes());
            case VOUCHER ->
                    voucherIssuingService.issueVoucherCode(request.tokenCap(), request.sessionLengthMinutes());
        };
        return ResponseEntity.ok(response);
    }
}
