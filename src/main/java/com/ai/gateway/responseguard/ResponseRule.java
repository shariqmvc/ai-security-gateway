package com.ai.gateway.responseguard;

public interface ResponseRule {

    ResponseResult evaluate(String response);

}
