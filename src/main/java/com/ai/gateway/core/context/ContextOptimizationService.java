package com.ai.gateway.core.context;

import com.ai.gateway.core.contract.ContextMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ContextOptimizationService {

    private static final int CHARS_PER_TOKEN = 4;
    private static final String COMPRESSION_MARKER =
            "\n\n[Earlier context compressed by AIRouter for token efficiency.]\n\n";

    private final ContextOptimizationProperties properties;

    public ContextOptimizationService(ContextOptimizationProperties properties) {
        this.properties = properties;
    }

    public ContextOptimizationResult optimize(String context) {
        return optimize(context, properties.getMaxInputTokens());
    }

    /**
     * Optimizes a flattened context against a model-specific input budget.
     */
    public ContextOptimizationResult optimize(String context, int maxInputTokens) {
        String original = context == null ? "" : context;
        int originalTokens = estimateTokens(original);
        return optimizeSegments(
                toSegments(original),
                original,
                originalTokens,
                maxInputTokens);
    }

    /**
     * Optimizes structured chat messages while preserving high-priority roles.
     * System/developer instructions and the latest user turn are protected;
     * recent turns are preferred over older middle context.
     */
    public ContextOptimizationResult optimize(List<ContextMessage> messages, int maxInputTokens) {
        List<ContextMessage> safeMessages = messages == null
                ? List.of()
                : messages.stream()
                .filter(m -> m != null && m.getContent() != null && !m.getContent().isBlank())
                .toList();

        String original = joinMessages(safeMessages);

        int originalTokens = estimateTokens(original);
        if (!properties.isEnabled() || original.isBlank()
                || originalTokens < properties.getMinInputTokens()) {
            return unchanged(original, originalTokens, "NO_OP");
        }

        List<ContextMessage> unique = deduplicateMessages(safeMessages);
        int duplicateRemoved = safeMessages.size() - unique.size();
        String deduplicated = joinMessages(unique);
        int deduplicatedTokens = estimateTokens(deduplicated);

        if (deduplicatedTokens <= maxInputTokens) {
            int saved = Math.max(0, originalTokens - deduplicatedTokens);
            if (saved < properties.getMinimumSavingsTokens()) {
                return unchanged(original, originalTokens, "NO_OP");
            }
            return result(original, deduplicated, originalTokens, deduplicatedTokens,
                    saved, duplicateRemoved, "DEDUPLICATION");
        }

        List<ContextMessage> selected = selectRoleAwareMessages(unique, maxInputTokens);
        String compressed = joinMessages(selected);
        compressed = fitWithMarker(compressed, maxInputTokens);
        int optimizedTokens = estimateTokens(compressed);
        int saved = Math.max(0, originalTokens - optimizedTokens);

        if (saved < properties.getMinimumSavingsTokens()
                || optimizedTokens > Math.max(256, maxInputTokens)) {
            return unchanged(original, originalTokens, "NO_OP_BUDGET_UNSAFE");
        }

        return result(original, compressed, originalTokens, optimizedTokens,
                saved, duplicateRemoved, "DEDUPLICATION_AND_MIDDLE_PRUNING_ROLE_AWARE");
    }

    private ContextOptimizationResult optimizeSegments(
            List<String> segments,
            String original,
            int originalTokens,
            int maxInputTokens) {

        if (!properties.isEnabled() || original.isBlank()
                || originalTokens < properties.getMinInputTokens()) {
            return unchanged(original, originalTokens, "NO_OP");
        }

        List<String> uniqueSegments = deduplicate(segments);
        int duplicateSegmentsRemoved = segments.size() - uniqueSegments.size();
        String deduplicated = joinSegments(uniqueSegments);
        int deduplicatedTokens = estimateTokens(deduplicated);
        int budget = Math.max(256, maxInputTokens);

        if (deduplicatedTokens <= budget) {
            int saved = Math.max(0, originalTokens - deduplicatedTokens);
            if (saved < properties.getMinimumSavingsTokens()) {
                return unchanged(original, originalTokens, "NO_OP");
            }
            return result(original, deduplicated, originalTokens, deduplicatedTokens,
                    saved, duplicateSegmentsRemoved, "DEDUPLICATION");
        }

        String compressed = pruneToBudget(uniqueSegments, budget);
        int optimizedTokens = estimateTokens(compressed);
        int saved = Math.max(0, originalTokens - optimizedTokens);
        if (saved < properties.getMinimumSavingsTokens() || optimizedTokens > budget) {
            return unchanged(original, originalTokens, "NO_OP_BUDGET_UNSAFE");
        }
        return result(original, compressed, originalTokens, optimizedTokens,
                saved, duplicateSegmentsRemoved, "DEDUPLICATION_AND_MIDDLE_PRUNING");
    }

    private List<ContextMessage> deduplicateMessages(List<ContextMessage> messages) {
        List<ContextMessage> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ContextMessage message : messages) {
            String key = normalize(message.getRole()) + "\u0000" + normalize(message.getContent());
            if (seen.add(key)) {
                result.add(message);
            }
        }
        return result;
    }

    private List<ContextMessage> selectRoleAwareMessages(List<ContextMessage> messages, int maxInputTokens) {
        int budget = Math.max(256, maxInputTokens);
        Set<Integer> selectedIndexes = new HashSet<>();

        // Protect system/developer instructions regardless of age.
        for (int i = 0; i < messages.size(); i++) {
            if (isProtectedRole(messages.get(i).getRole())) {
                selectedIndexes.add(i);
            }
        }

        // Protect the latest user turn.
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equalsIgnoreCase(messages.get(i).getRole())) {
                selectedIndexes.add(i);
                break;
            }
        }

        // Prefer the most recent turns after protected messages.
        for (int i = messages.size() - 1; i >= 0 && selectedIndexes.size() < messages.size(); i--) {
            if (selectedIndexes.contains(i)) continue;
            selectedIndexes.add(i);
            if (estimateTokens(joinSelected(messages, selectedIndexes)) >= budget) {
                selectedIndexes.remove(i);
                break;
            }
        }

        // Fill remaining budget from older middle context in chronological order.
        for (int i = 0; i < messages.size(); i++) {
            if (selectedIndexes.contains(i)) continue;
            Set<Integer> tentative = new HashSet<>(selectedIndexes);
            tentative.add(i);
            if (estimateTokens(joinSelected(messages, tentative)) > budget) break;
            selectedIndexes.add(i);
        }

        List<ContextMessage> selected = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            if (selectedIndexes.contains(i)) selected.add(messages.get(i));
        }
        return selected;
    }

    private String joinSelected(List<ContextMessage> messages, Set<Integer> indexes) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            if (indexes.contains(i)) values.add(formatMessage(messages.get(i)));
        }
        return String.join("\n\n", values);
    }

    private boolean isProtectedRole(String role) {
        return "system".equalsIgnoreCase(role)
                || "developer".equalsIgnoreCase(role);
    }

    private String fitWithMarker(String value, int budget) {
        if (estimateTokens(value) <= Math.max(256, budget)) return value;
        int markerTokens = estimateTokens(COMPRESSION_MARKER);
        int remaining = Math.max(1, budget - markerTokens);
        int prefix = Math.max(1, remaining / 3);
        int suffix = Math.max(1, remaining - prefix);
        return takeTokens(value, prefix) + COMPRESSION_MARKER + takeTokensFromEnd(value, suffix);
    }

    private List<String> toSegments(String context) {
        String normalized = context.replace("\r\n", "\n").trim();
        if (normalized.isEmpty()) return List.of();
        String[] raw = normalized.split("\\n\\s*\\n+");
        List<String> segments = new ArrayList<>();
        int max = Math.max(1, properties.getMaxSegments());
        for (String value : raw) {
            String segment = value == null ? "" : value.trim();
            if (!segment.isEmpty()) segments.add(segment);
            if (segments.size() >= max) break;
        }
        return segments.isEmpty() ? List.of(normalized) : segments;
    }

    private List<String> deduplicate(List<String> segments) {
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String segment : segments) {
            String key = normalize(segment);
            if (seen.add(key)) result.add(segment);
        }
        return result;
    }

    private String pruneToBudget(List<String> segments, int maxTokens) {
        if (segments.isEmpty()) return "";
        int budget = Math.max(256, maxTokens);
        String prefix = takeTokens(joinSegments(segments.subList(0, Math.min(segments.size(), 3))),
                Math.min(properties.getPrefixTokens(), budget / 3));
        String suffix = takeTokensFromEnd(joinSegments(segments.subList(Math.max(0, segments.size() - 6), segments.size())),
                Math.min(properties.getSuffixTokens(), budget / 2));
        int markerTokens = estimateTokens(COMPRESSION_MARKER);
        int middleBudget = budget - estimateTokens(prefix) - estimateTokens(suffix) - markerTokens;
        StringBuilder middle = new StringBuilder();
        for (int i = 3; i < Math.max(3, segments.size() - 6); i++) {
            String tentative = middle.length() == 0 ? segments.get(i) : middle + "\n\n" + segments.get(i);
            if (estimateTokens(tentative) > middleBudget) break;
            middle.setLength(0);
            middle.append(tentative);
        }
        String result = prefix;
        if (middle.length() > 0) result += "\n\n" + middle;
        result += COMPRESSION_MARKER + suffix;
        return takeTokens(result, budget);
    }

    private String joinSegments(List<String> segments) { return String.join("\n\n", segments); }
    private String joinMessages(List<ContextMessage> messages) {
        return messages.stream()
                .map(this::formatMessage)
                .filter(s -> !s.isBlank())
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");
    }

    /**
     * AIRouter currently flattens multi-turn context into a single provider
     * user message for providers such as Ollama. Preserve the semantic role
     * explicitly so the model can distinguish prior user turns from assistant
     * answers instead of treating the entire conversation as one undifferentiated
     * block of user text.
     */
    private String formatMessage(ContextMessage message) {
        if (message == null || message.getContent() == null || message.getContent().isBlank()) {
            return "";
        }

        String role = message.getRole() == null || message.getRole().isBlank()
                ? "message"
                : message.getRole().trim().toLowerCase(Locale.ROOT);

        String label = switch (role) {
            case "user" -> "User";
            case "assistant" -> "Assistant";
            case "system" -> "System";
            case "developer" -> "Developer";
            case "tool" -> "Tool";
            default -> "Role: " + role;
        };

        return label + ":\n" + message.getContent().trim();
    }
    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
    private String takeTokens(String value, int tokenBudget) {
        if (value == null || value.isEmpty()) return "";
        int maxChars = Math.max(1, tokenBudget) * CHARS_PER_TOKEN;
        return value.length() <= maxChars ? value : value.substring(0, Math.max(1, maxChars - 1)) + "…";
    }
    private String takeTokensFromEnd(String value, int tokenBudget) {
        if (value == null || value.isEmpty()) return "";
        int maxChars = Math.max(1, tokenBudget) * CHARS_PER_TOKEN;
        return value.length() <= maxChars ? value : "…" + value.substring(Math.max(0, value.length() - maxChars + 1));
    }
    private int estimateTokens(String value) {
        if (value == null || value.isBlank()) return 0;
        return Math.max(1, (value.length() + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN);
    }
    private ContextOptimizationResult result(String original, String optimized, int originalTokens,
                                             int optimizedTokens, int saved, int duplicates, String strategy) {
        return ContextOptimizationResult.builder().originalContext(original).optimizedContext(optimized)
                .originalTokens(originalTokens).optimizedTokens(optimizedTokens).tokensSaved(saved)
                .duplicateSegmentsRemoved(duplicates).compressed(true).strategy(strategy).build();
    }
    private ContextOptimizationResult unchanged(String context, int tokens, String strategy) {
        return ContextOptimizationResult.builder().originalContext(context).optimizedContext(context)
                .originalTokens(tokens).optimizedTokens(tokens).tokensSaved(0).duplicateSegmentsRemoved(0)
                .compressed(false).strategy(strategy).build();
    }
}
