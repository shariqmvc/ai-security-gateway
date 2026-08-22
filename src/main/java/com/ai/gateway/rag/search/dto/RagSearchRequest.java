package com.ai.gateway.rag.search.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RagSearchRequest {

    @NotBlank(message = "Search query is required.")
    @Size(max = 10000, message = "Search query must not exceed 10000 characters.")
    private String query;

    @Min(value = 1, message = "topK must be at least 1.")
    @Max(value = 100, message = "topK must not exceed 100.")
    @Builder.Default
    private int topK = 5;

    @DecimalMin(value = "-1.0", message = "minScore must be at least -1.0.")
    @DecimalMax(value = "1.0", message = "minScore must not exceed 1.0.")
    @Builder.Default
    private double minScore = -1.0d;
}
