package com.ai.gateway.personal.apikey.repository;

import com.ai.gateway.personal.apikey.entity.PersonalApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PersonalApiKeyRepository extends JpaRepository<PersonalApiKey, UUID> {
    Optional<PersonalApiKey> findByKeyHash(String keyHash);
    List<PersonalApiKey> findByPersonalAccountIdOrderByCreatedAtDesc(UUID personalAccountId);
    Optional<PersonalApiKey> findByIdAndPersonalAccountId(UUID id, UUID personalAccountId);
}
