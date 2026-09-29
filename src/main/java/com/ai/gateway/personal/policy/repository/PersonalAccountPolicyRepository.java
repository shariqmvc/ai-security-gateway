package com.ai.gateway.personal.policy.repository;

import com.ai.gateway.personal.policy.entity.PersonalAccountPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PersonalAccountPolicyRepository extends JpaRepository<PersonalAccountPolicy, UUID> {
    Optional<PersonalAccountPolicy> findByPersonalAccountId(UUID personalAccountId);
}
