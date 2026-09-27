package com.ai.gateway.context;

import com.ai.gateway.dto.ChatMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class ConversationContextAssembler {

    private static final String HISTORY_HEADER =
            "[BEGIN PRIOR CONVERSATION CONTEXT]";
    private static final String HISTORY_FOOTER =
            "[END PRIOR CONVERSATION CONTEXT]";

    private final int maxCharacters;

    public ConversationContextAssembler(
            @Value("${gateway.conversation-context.max-characters:12000}") int maxCharacters) {
        this.maxCharacters = Math.max(1000, maxCharacters);
    }

    public String assemble(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        StringBuilder history = new StringBuilder();
        history.append(HISTORY_HEADER).append('\n');

        int used = 0;

        // Keep the most recent turns first. This preserves the current topic
        // when a long-running conversation exceeds the context budget.
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message == null || isBlank(message.getRole()) || isBlank(message.getContent())) {
                continue;
            }

            String role = normalizeRole(message.getRole());
            String content = message.getContent().trim();
            String entry = "[" + role + "]\n" + content + "\n";

            if (used + entry.length() > maxCharacters) {
                break;
            }

            history.insert(HISTORY_HEADER.length() + 1, entry);
            used += entry.length();
        }

        if (used == 0) {
            return "";
        }

        history.append(HISTORY_FOOTER);
        return history.toString();
    }

    public String combine(String conversationContext, String currentProviderPrompt) {
        if (conversationContext == null || conversationContext.isBlank()) {
            return currentProviderPrompt;
        }
        return conversationContext + "\n\n[BEGIN CURRENT REQUEST]\n"
                + currentProviderPrompt
                + "\n[END CURRENT REQUEST]";
    }

    private String normalizeRole(String role) {
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "user", "assistant", "system" -> normalized;
            default -> "user";
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
