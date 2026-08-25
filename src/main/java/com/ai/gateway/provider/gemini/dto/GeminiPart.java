package com.ai.gateway.provider.gemini.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeminiPart {

    private String text;

    @com.fasterxml.jackson.annotation.JsonProperty("inlineData")
    private InlineData inlineData;

    @com.fasterxml.jackson.annotation.JsonProperty("fileData")
    private FileData fileData;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InlineData {
        private String mimeType;
        private String data;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FileData {
        private String mimeType;
        private String fileUri;
    }

}
