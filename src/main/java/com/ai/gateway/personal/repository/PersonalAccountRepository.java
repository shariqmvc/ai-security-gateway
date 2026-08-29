package com.ai.gateway.personal.repository;

import com.ai.gateway.personal.entity.PersonalAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PersonalAccountRepository extends JpaRepository<PersonalAccount, UUID> {

    Optional<PersonalAccount> findByUserId(UUID userId);
}
