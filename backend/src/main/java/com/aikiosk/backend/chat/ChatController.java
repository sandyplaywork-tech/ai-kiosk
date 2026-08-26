package com.aikiosk.backend.chat;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final ChatExportService chatExportService;

    public ChatController(ChatService chatService, ChatExportService chatExportService) {
        this.chatService = chatService;
        this.chatExportService = chatExportService;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        return ResponseEntity.ok(chatService.chat(request));
    }

    /**
     * Lets the frontend redisplay prior turns after a resumed login - without
     * this, a resumed session would still have full LLM context server-side
     * but would look like a blank chat window.
     */
    @GetMapping("/{sessionId}/history")
    public ResponseEntity<List<ChatTurn>> history(@PathVariable String sessionId) {
        return ResponseEntity.ok(chatService.history(sessionId));
    }

    @PostMapping("/export")
    public ResponseEntity<Map<String, String>> export(@RequestBody ExportRequest request) {
        chatExportService.export(request);
        return ResponseEntity.ok(Map.of("status", "sent"));
    }
}
