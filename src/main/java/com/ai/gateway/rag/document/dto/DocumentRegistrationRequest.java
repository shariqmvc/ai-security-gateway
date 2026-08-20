package com.ai.gateway.rag.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentRegistrationRequest {

    @NotBlank
    @Size(max = 500)
    private String fileName;

    @Size(max = 255)
    private String contentType;

    private Long fileSizeBytes;

    @Size(max = 64)
    private String checksumSha256;

    /*
     * Foundation-only registration. Actual multipart parsing, extraction,
     * chunking and embedding are implemented by the subsequent ingestion
     * phases. Keeping content optional allows metadata-only registration.
     */
    private String content;
}
