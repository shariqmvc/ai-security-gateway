package com.ai.gateway.rag.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Record-boundary chunker for structured JSON knowledge sources.
 *
 * Each top-level record becomes one logical retrieval unit. The embedding
 * representation is canonical text rather than raw JSON syntax so retrieval
 * focuses on field meaning while preserving record identity in metadata.
 */
@Component
@RequiredArgsConstructor
public class RecordAwareDocumentChunker implements DocumentChunker {

    private static final List<String> RECORD_CONTAINER_FIELDS =
            List.of("records", "data", "items", "entries");

    private static final List<String> RECORD_ID_FIELDS =
            List.of("recordId", "record_id", "id", "key");

    private static final List<String> RECORD_TYPE_FIELDS =
            List.of("recordType", "record_type", "type");

    private static final int APPROX_CHARS_PER_TOKEN = 4;

    private final ObjectMapper objectMapper;
    private final RagIngestionProperties properties;

    @Override
    public List<DocumentChunk> chunk(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) {
            throw new DocumentIngestionException("Structured document text is empty.");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(normalizedText);
        } catch (JsonProcessingException ex) {
            throw new DocumentIngestionException(
                    "RECORD_AWARE chunking requires valid JSON content.", ex);
        }

        List<JsonNode> records = extractRecords(root);
        if (records.isEmpty()) {
            throw new DocumentIngestionException(
                    "RECORD_AWARE chunking found no records in the JSON document.");
        }

        if (records.size() > properties.getMaxChunks()) {
            throw new DocumentIngestionException(
                    "Document exceeds the maximum supported record count: "
                            + properties.getMaxChunks());
        }

        List<DocumentChunk> chunks = new ArrayList<>(records.size());
        for (int index = 0; index < records.size(); index++) {
            JsonNode record = records.get(index);
            if (!record.isObject()) {
                throw new DocumentIngestionException(
                        "RECORD_AWARE chunking requires each record to be a JSON object.");
            }

            String recordId = firstText(record, RECORD_ID_FIELDS);
            if (recordId == null || recordId.isBlank()) {
                recordId = "record-" + index;
            }

            String recordType = firstText(record, RECORD_TYPE_FIELDS);
            String content = canonicalizeRecord(record);
            int tokenCount = estimateTokens(content);

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("chunkIndex", index);
            metadata.put("recordIndex", index);
            metadata.put("recordId", recordId);
            metadata.put("chunkId", recordId + "-chunk-" + index);
            if (recordType != null && !recordType.isBlank()) {
                metadata.put("recordType", recordType);
            }
            metadata.put("estimatedTokenCount", tokenCount);
            metadata.put("chunkingStrategy", "RECORD_AWARE");

            try {
                chunks.add(new DocumentChunk(
                        index,
                        content,
                        tokenCount,
                        objectMapper.writeValueAsString(metadata),
                        recordId,
                        null,
                        recordId + "-chunk-" + index));
            } catch (JsonProcessingException ex) {
                throw new DocumentIngestionException(
                        "Unable to serialize record-aware chunk metadata.", ex);
            }
        }

        return chunks;
    }

    private List<JsonNode> extractRecords(JsonNode root) {
        if (root.isArray()) {
            List<JsonNode> records = new ArrayList<>();
            root.elements().forEachRemaining(records::add);
            return records;
        }

        if (!root.isObject()) {
            throw new DocumentIngestionException(
                    "RECORD_AWARE chunking requires a JSON object or array.");
        }

        for (String field : RECORD_CONTAINER_FIELDS) {
            JsonNode candidate = root.get(field);
            if (candidate != null && candidate.isArray()) {
                List<JsonNode> records = new ArrayList<>();
                candidate.elements().forEachRemaining(records::add);
                return records;
            }
        }

        return List.of(root);
    }

    private String firstText(JsonNode record, List<String> fields) {
        for (String field : fields) {
            JsonNode value = record.get(field);
            if (value != null && value.isValueNode() && !value.isNull()) {
                String text = value.asText();
                if (text != null && !text.isBlank()) {
                    return text.trim();
                }
            }
        }
        return null;
    }

    private String canonicalizeRecord(JsonNode record) {
        StringBuilder builder = new StringBuilder();
        Iterator<Map.Entry<String, JsonNode>> fields = record.fields();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String label = humanize(field.getKey());
            appendField(builder, label, field.getValue(), 0);
        }

        return builder.toString().trim();
    }

    private void appendField(
            StringBuilder builder,
            String label,
            JsonNode value,
            int depth) {

        if (value == null || value.isNull()) {
            return;
        }

        if (value.isValueNode()) {
            String text = value.asText();
            if (text != null && !text.isBlank()) {
                builder.append(label)
                        .append(": ")
                        .append(text.trim())
                        .append('\n');
            }
            return;
        }

        if (value.isArray()) {
            for (JsonNode item : value) {
                appendField(builder, label, item, depth + 1);
            }
            return;
        }

        if (value.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> children = value.fields();
            while (children.hasNext()) {
                Map.Entry<String, JsonNode> child = children.next();
                appendField(
                        builder,
                        label + " " + humanize(child.getKey()),
                        child.getValue(),
                        depth + 1);
            }
        }
    }

    private String humanize(String value) {
        if (value == null || value.isBlank()) {
            return "Field";
        }

        String normalized = value
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .trim();

        return normalized.isEmpty()
                ? "Field"
                : Character.toUpperCase(normalized.charAt(0))
                        + normalized.substring(1);
    }

    private int estimateTokens(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Math.max(
                1,
                (value.length() + APPROX_CHARS_PER_TOKEN - 1)
                        / APPROX_CHARS_PER_TOKEN);
    }
}
