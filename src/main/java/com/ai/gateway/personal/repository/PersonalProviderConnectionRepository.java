package com.ai.gateway.personal.repository;

import com.ai.gateway.core.model.Provider;
import com.ai.gateway.personal.entity.PersonalProviderConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PersonalProviderConnectionRepository
        extends JpaRepository<PersonalProviderConnection, UUID> {

    List<PersonalProviderConnection> findAllByPersonalAccountIdOrderByProviderAsc(UUID personalAccountId);

    Optional<PersonalProviderConnection> findByPersonalAccountIdAndProvider(
            UUID personalAccountId,
            Provider provider);

    boolean existsByPersonalAccountIdAndProvider(
            UUID personalAccountId,
            Provider provider);
}
