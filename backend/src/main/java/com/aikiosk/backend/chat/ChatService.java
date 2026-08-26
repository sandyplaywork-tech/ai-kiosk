package com.aikiosk.backend.chat;

import com.aikiosk.backend.config.KioskProperties;
import com.aikiosk.backend.session.SessionExpiredException;
import com.aikiosk.backend.session.SessionMeta;
import com.aikiosk.backend.session.SessionService;
import com.aikiosk.backend.session.SessionStatus;
import com.aikiosk.backend.session.TokenLimitReachedException;
import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.TextBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final SessionService sessionService;
    private final AnthropicClient anthropicClient;
    private final KioskProperties kioskProperties;
    private final ChatArchiveService chatArchiveService;

    public ChatService(
            SessionService sessionService,
            AnthropicClient anthropicClient,
            KioskProperties kioskProperties,
            ChatArchiveService chatArchiveService) {
        this.sessionService = sessionService;
        this.anthropicClient = anthropicClient;
        this.kioskProperties = kioskProperties;
        this.chatArchiveService = chatArchiveService;
    }

    public ChatResponse chat(ChatRequest request) {
        // Dual limit check: the session key existing at all means time hasn't
        // run out (Redis TTL already deleted it otherwise); tokensRemaining is
        // checked explicitly since it doesn't self-enforce like the TTL does.
        SessionStatus status = sessionService.getStatus(request.sessionId())
                .orElseThrow(SessionExpiredException::new);

        // A paused (logged-out, grace-period) session isn't chattable until
        // it's resumed via login - treat it the same as not found.
        if (sessionService.isPaused(request.sessionId())) {
            throw new SessionExpiredException();
        }

        if (status.tokensRemaining() <= 0) {
            throw new TokenLimitReachedException();
        }

        List<MessageParam> messages = new ArrayList<>();
        for (ChatTurn turn : sessionService.getMessages(request.sessionId())) {
            MessageParam.Role role = "assistant".equals(turn.role())
                    ? MessageParam.Role.ASSISTANT
                    : MessageParam.Role.USER;
            messages.add(MessageParam.builder().role(role).content(turn.content()).build());
        }
        messages.add(MessageParam.builder().role(MessageParam.Role.USER).content(request.message()).build());

        int maxTokens = Math.max(1, Math.min(kioskProperties.getLlm().getMaxTokens(), status.tokensRemaining()));

        MessageCreateParams params = MessageCreateParams.builder()
                .model(kioskProperties.getLlm().getModel())
                .maxTokens(maxTokens)
                .messages(messages)
                .build();

        Message response = anthropicClient.messages().create(params);

        String replyText = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(TextBlock::text)
                .collect(Collectors.joining());

        long tokensUsed = response.usage().inputTokens() + response.usage().outputTokens();
        sessionService.deductTokens(request.sessionId(), tokensUsed);

        sessionService.appendMessage(request.sessionId(), "user", request.message());
        sessionService.appendMessage(request.sessionId(), "assistant", replyText);
        archiveBestEffort(request.sessionId());

        SessionStatus updated = sessionService.getStatus(request.sessionId()).orElseThrow(SessionExpiredException::new);

        return new ChatResponse(replyText, updated.tokensRemaining(), updated.timeRemainingSeconds());
    }

    public List<ChatTurn> history(String sessionId) {
        return sessionService.getMessages(sessionId);
    }

    /**
     * The Postgres archive is a compliance/audit copy, not something the live
     * chat should ever fail on - a hiccup here is logged and swallowed rather
     * than surfaced to the customer.
     */
    private void archiveBestEffort(String sessionId) {
        try {
            SessionMeta meta = sessionService.getMeta(sessionId).orElse(null);
            if (meta == null) {
                return;
            }
            chatArchiveService.archive(sessionId, meta.voucherId(), meta.startedAt(), sessionService.getMessages(sessionId));
        } catch (RuntimeException e) {
            log.warn("Failed to archive chat session {} to Postgres", sessionId, e);
        }
    }
}
