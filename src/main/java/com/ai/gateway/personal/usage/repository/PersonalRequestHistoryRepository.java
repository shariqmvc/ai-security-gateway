package com.ai.gateway.personal.usage.repository;
import com.ai.gateway.personal.usage.entity.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.*;
import java.math.BigDecimal;

public interface PersonalRequestHistoryRepository extends JpaRepository<PersonalRequestHistory,UUID> {
 Page<PersonalRequestHistory> findByPersonalAccountIdOrderByCreatedAtDesc(UUID accountId,Pageable pageable);
 Optional<PersonalRequestHistory> findByRequestIdAndPersonalAccountId(UUID requestId,UUID accountId);
 long countByPersonalAccountIdAndCreatedAtGreaterThanEqual(UUID accountId,LocalDateTime since);
 long countByPersonalAccountIdAndStatusAndCreatedAtGreaterThanEqual(UUID accountId,PersonalRequestStatus status,LocalDateTime since);
 @Query("select coalesce(sum(h.inputTokens),0),coalesce(sum(h.outputTokens),0),coalesce(sum(h.totalTokens),0),coalesce(sum(h.cost),0) from PersonalRequestHistory h where h.personalAccountId=:accountId and h.status=:status and h.createdAt>=:since")
 List<Object[]> aggregateSince(@Param("accountId") UUID accountId,@Param("status") PersonalRequestStatus status,@Param("since") LocalDateTime since);
 @Query("select coalesce(sum(h.estimatedTokensSaved),0),coalesce(sum(case when h.cacheHit=true then 1 else 0 end),0) from PersonalRequestHistory h where h.personalAccountId=:accountId and h.createdAt>=:since")
 List<Object[]> optimizationSummary(@Param("accountId") UUID accountId,@Param("since") LocalDateTime since);
}
