package com.aikiosk.backend.session;

import com.aikiosk.backend.config.KioskProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/session")
public class SessionStatusController {

    private final SessionService sessionService;
    private final SessionExtendService sessionExtendService;
    private final KioskProperties kioskProperties;

    public SessionStatusController(
            SessionService sessionService,
            SessionExtendService sessionExtendService,
            KioskProperties kioskProperties) {
        this.sessionService = sessionService;
        this.sessionExtendService = sessionExtendService;
        this.kioskProperties = kioskProperties;
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<Map<String, Object>> status(@PathVariable String sessionId) {
        return sessionService.getStatus(sessionId)
                .<ResponseEntity<Map<String, Object>>>map(status -> ResponseEntity.ok(Map.of(
                        "status", sessionService.isPaused(sessionId) ? "paused" : "active",
                        "tokenCap", status.tokenCap(),
                        "tokensRemaining", status.tokensRemaining(),
                        "timeRemainingSeconds", status.timeRemainingSeconds(),
                        "warningMinutesRemaining", kioskProperties.getSession().getWarningMinutesRemaining(),
                        "warningTokenPercent", kioskProperties.getSession().getWarningTokenPercent())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "expired")));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestBody LogoutRequest request) {
        sessionService.pauseSession(request.sessionId(), kioskProperties.getSession().getLogoutGraceMinutes());
        return ResponseEntity.ok(Map.of("status", "ended"));
    }

    @PostMapping("/extend")
    public ResponseEntity<ExtendResponse> extend(@RequestBody ExtendRequest request) {
        return ResponseEntity.ok(sessionExtendService.extend(request));
    }
}
