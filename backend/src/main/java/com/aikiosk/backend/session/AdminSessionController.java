package com.aikiosk.backend.session;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Staff-facing. Guarded by AdminApiKeyFilter (X-Admin-Key header), same as
 * voucher issuance - no separate staff-account system yet.
 */
@RestController
@RequestMapping("/api/admin/sessions")
public class AdminSessionController {

    private final AdminSessionService adminSessionService;

    public AdminSessionController(AdminSessionService adminSessionService) {
        this.adminSessionService = adminSessionService;
    }

    @GetMapping
    public ResponseEntity<List<AdminSessionSummary>> list() {
        return ResponseEntity.ok(adminSessionService.listActiveSessions());
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> kill(@PathVariable String sessionId) {
        adminSessionService.killSession(sessionId);
        return ResponseEntity.noContent().build();
    }
}
