package com.ai.gateway.personal.quota.repository;
import com.ai.gateway.enums.QuotaPeriodType;
import com.ai.gateway.personal.quota.entity.PersonalQuotaUsage;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;

public interface PersonalQuotaUsageRepository extends JpaRepository<PersonalQuotaUsage,UUID> {
 Optional<PersonalQuotaUsage> findByPersonalAccountIdAndPeriodTypeAndPeriodStart(UUID accountId,QuotaPeriodType periodType,LocalDate periodStart);

 @Modifying
 @Query(value="""
 INSERT INTO PERSONAL_QUOTA_USAGE
   (id,personal_account_id,period_type,period_start,request_count,token_count,version)
 SELECT gen_random_uuid(),:accountId,'DAILY',:periodStart,1,0,0
 WHERE :limit > 0
 ON CONFLICT (personal_account_id,period_type,period_start)
 DO UPDATE SET request_count=PERSONAL_QUOTA_USAGE.request_count+1
 WHERE PERSONAL_QUOTA_USAGE.request_count < :limit
 """,nativeQuery=true)
 int consumeDailyRequest(@Param("accountId") UUID accountId,@Param("periodStart") LocalDate periodStart,@Param("limit") long limit);

 @Modifying
 @Query(value="""
 INSERT INTO PERSONAL_QUOTA_USAGE
   (id,personal_account_id,period_type,period_start,request_count,token_count,version)
 SELECT gen_random_uuid(),:accountId,'MONTHLY',:periodStart,0,:tokens,0
 WHERE :limit > 0 AND :tokens <= :limit
 ON CONFLICT (personal_account_id,period_type,period_start)
 DO UPDATE SET token_count=PERSONAL_QUOTA_USAGE.token_count+EXCLUDED.token_count
 WHERE PERSONAL_QUOTA_USAGE.token_count+EXCLUDED.token_count <= :limit
 """,nativeQuery=true)
 int consumeMonthlyTokens(@Param("accountId") UUID accountId,@Param("periodStart") LocalDate periodStart,
                          @Param("tokens") long tokens,@Param("limit") long limit);
}
