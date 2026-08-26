package com.aikiosk.backend.chat;

import java.util.List;

/**
 * Shared plain-text rendering of a conversation - used for both the emailed
 * export and the Postgres archive, so the two don't drift.
 */
final class ChatTranscriptFormatter {

    private ChatTranscriptFormatter() {
    }

    static String format(List<ChatTurn> turns) {
        StringBuilder sb = new StringBuilder();

        if (turns.isEmpty()) {
            sb.append("(No messages were exchanged in this session.)\n");
        } else {
            for (ChatTurn turn : turns) {
                String label = "assistant".equals(turn.role()) ? "Assistant" : "You";
                sb.append(label).append(": ").append(turn.content()).append("\n\n");
            }
        }

        return sb.toString();
    }
}
