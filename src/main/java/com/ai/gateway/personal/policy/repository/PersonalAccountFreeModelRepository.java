package com.ai.gateway.personal.policy.repository;

import com.ai.gateway.personal.policy.entity.PersonalAccountFreeModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PersonalAccountFreeModelRepository extends JpaRepository<PersonalAccountFreeModel, UUID> {
    List<PersonalAccountFreeModel> findAllByPersonalAccountId(UUID personalAccountId);
}
