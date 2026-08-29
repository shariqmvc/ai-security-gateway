package com.ai.gateway.personal.repository;

import com.ai.gateway.personal.entity.PersonalUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PersonalUserRepository extends JpaRepository<PersonalUser, UUID> {

    Optional<PersonalUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
