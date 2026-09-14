package com.ai.gateway.personal.rag;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonalRagServiceJdbcStatusRegressionTest {

    @Test
    void jdbcStatusArgumentsMustBePersistedAsNames() throws Exception {
        Path source = Path.of("src/main/java/com/ai/gateway/personal/rag/PersonalRagService.java");
        String text = Files.readString(source);

        assertTrue(text.contains("DocumentStatus.READY_FOR_EMBEDDING.name()"),
                "READY_FOR_EMBEDDING must be converted to its VARCHAR representation");
        assertTrue(text.contains("DocumentStatus.FAILED.name()"),
                "FAILED must be converted to its VARCHAR representation");
    }
}
