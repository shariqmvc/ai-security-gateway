package com.ai.gateway.service;

import java.util.UUID;

public interface RestoreService {

    String restore(String response, UUID requestId);
}
