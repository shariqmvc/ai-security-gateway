package com.ai.gateway.quota.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuotaUsageDto {

    private Long used;

    private Long limit;

    private Long remaining;
}