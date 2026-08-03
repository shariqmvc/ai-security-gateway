package com.ai.gateway.responseguard;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ResponseResult {

    private boolean allowed;

    private String reason;

}
