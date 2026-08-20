package com.ai.gateway.rag.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, UUID> {
    List<KnowledgeBase> findAllByOrderByCreatedAtDesc();
    boolean existsByNameIgnoreCase(String name);
}
