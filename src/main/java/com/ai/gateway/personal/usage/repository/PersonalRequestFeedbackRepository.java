package com.ai.gateway.personal.usage.repository;

import com.ai.gateway.personal.usage.entity.PersonalRequestFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PersonalRequestFeedbackRepository extends JpaRepository<PersonalRequestFeedback,UUID> {
 Optional<PersonalRequestFeedback> findByRequestIdAndPersonalAccountId(UUID requestId,UUID personalAccountId);
}
