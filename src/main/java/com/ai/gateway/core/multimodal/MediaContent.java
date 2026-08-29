package com.ai.gateway.core.multimodal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaContent {

    @NotNull(message = "Media type is required.")
    private MediaTypeKind type;

    @NotNull(message = "Media source type is required.")
    private MediaSourceType sourceType;

    @NotBlank(message = "Media MIME type is required.")
    @Size(max = 100, message = "Media MIME type is too long.")
    private String mimeType;

    @Size(max = 8192, message = "Media URL is too long.")
    private String url;

    @Size(max = 15_000_000, message = "Base64 media payload exceeds the gateway limit.")
    private String data;

    @Size(max = 1000, message = "Media detail is too long.")
    private String detail;
}
