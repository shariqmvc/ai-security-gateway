package com.ai.gateway.personal.policy.repository;

import com.ai.gateway.personal.policy.entity.PersonalAccountFeature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PersonalAccountFeatureRepository extends JpaRepository<PersonalAccountFeature, UUID> {
    List<PersonalAccountFeature> findAllByPersonalAccountId(UUID personalAccountId);
}
