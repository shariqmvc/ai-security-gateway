package com.ai.gateway.personal.preferences.repository;

import com.ai.gateway.personal.preferences.entity.PersonalAccountPreferences;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PersonalAccountPreferencesRepository
        extends JpaRepository<PersonalAccountPreferences, UUID> {

    Optional<PersonalAccountPreferences> findByPersonalAccountId(UUID personalAccountId);
}
