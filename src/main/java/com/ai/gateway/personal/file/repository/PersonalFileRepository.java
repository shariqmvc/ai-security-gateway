package com.ai.gateway.personal.file.repository;
import com.ai.gateway.personal.file.entity.PersonalFile; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface PersonalFileRepository extends JpaRepository<PersonalFile,UUID>{ Optional<PersonalFile> findByIdAndPersonalAccountId(UUID id,UUID personalAccountId); }
