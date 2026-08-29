package com.ai.gateway.core.responseguard;

public interface ResponseRule {

    ResponseResult evaluate(String response);

}
