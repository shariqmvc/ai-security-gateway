package com.ai.gateway.personal.usage.service;

import com.ai.gateway.personal.usage.entity.PersonalResponseRating;

import java.util.UUID;

public interface PersonalResponseFeedbackService {
 void rate(UUID accountId, UUID requestId, PersonalResponseRating rating);
 void remove(UUID accountId, UUID requestId);
}
