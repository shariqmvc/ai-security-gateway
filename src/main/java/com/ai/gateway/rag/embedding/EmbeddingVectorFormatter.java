package com.ai.gateway.rag.embedding;

import java.math.BigDecimal;

/** Serializes vectors using pgvector's textual input format. */
public final class EmbeddingVectorFormatter {
    private EmbeddingVectorFormatter() {}

    public static String toPgVector(EmbeddingVector vector) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < vector.values().size(); i++) {
            if (i > 0) builder.append(',');
            builder.append(BigDecimal.valueOf(vector.values().get(i).doubleValue()).stripTrailingZeros().toPlainString());
        }
        return builder.append(']').toString();
    }
}
