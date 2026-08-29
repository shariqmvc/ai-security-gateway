package com.ai.gateway.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;

class PackageBoundaryTest {

    @Test
    void coreMustNotImportBusinessOrPersonalPackages() throws IOException {
        Path root = Path.of("src/main/java/com/ai/gateway/core");
        if (!Files.exists(root)) {
            return;
        }

        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            String source = Files.readString(path);
                            assertFalse(source.contains("com.ai.gateway.business."),
                                    () -> "Core imports Business: " + path);
                            assertFalse(source.contains("com.ai.gateway.personal."),
                                    () -> "Core imports Personal: " + path);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }
}
