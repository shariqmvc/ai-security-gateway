package com.ai.gateway.personal.usage.service;
import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.core.contract.*;
import com.ai.gateway.personal.usage.dto.*;
import com.ai.gateway.personal.usage.entity.*;
import com.ai.gateway.personal.usage.repository.PersonalRequestHistoryRepository;
import com.ai.gateway.personal.quota.config.PersonalQuotaProperties;
import com.ai.gateway.personal.quota.repository.PersonalQuotaUsageRepository;
import com.ai.gateway.enums.QuotaPeriodType;
import com.ai.gateway.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.UUID;
import java.math.BigDecimal;

@Service @RequiredArgsConstructor
public class PersonalRequestHistoryServiceImpl implements PersonalRequestHistoryService {
 private final PersonalRequestHistoryRepository repository;
 private final PersonalQuotaProperties quotaProperties;
 private final PersonalQuotaUsageRepository quotaRepository;

 @Override @Transactional
 public void recordSuccess(UUID id,AuthenticationContext auth,AIRequest req,AIResponse resp,String prompt,
   long latency,long providerLatency,Integer estIn,Integer estOpt,Integer saved,Integer window,
   boolean cacheHit,boolean rag) {
  if(!personal(auth))return;
  var u=resp==null?null:resp.getUsage();
  repository.save(PersonalRequestHistory.builder().requestId(id).personalAccountId(auth.getPersonalAccountId())
   .status(PersonalRequestStatus.SUCCESS).provider(req.getProvider()).model(req.getModel())
   .billingMode(req.getBillingMode()).routingStrategy(req.getRoutingStrategy()==null?null:req.getRoutingStrategy().name())
   .maskedPrompt(prompt).maskedResponse(resp==null?null:resp.getResponse())
   .inputTokens(u==null?null:u.getInputTokens()).outputTokens(u==null?null:u.getOutputTokens())
   .totalTokens(u==null?null:u.getTotalTokens()).estimatedInputTokens(estIn)
   .estimatedOptimizedTokens(estOpt).estimatedTokensSaved(saved).contextWindowTokens(window)
   .latencyMs(latency).providerLatencyMs(providerLatency).cacheHit(cacheHit).ragEnabled(rag).build());
 }
 @Override @Transactional
 public void recordFailure(UUID id,AuthenticationContext auth,AIRequest req,String prompt,long latency,long providerLatency,String category){
  if(!personal(auth))return;
  repository.save(PersonalRequestHistory.builder().requestId(id).personalAccountId(auth.getPersonalAccountId())
   .status(PersonalRequestStatus.FAILED).provider(req==null?null:req.getProvider()).model(req==null?null:req.getModel())
   .billingMode(req==null?null:req.getBillingMode())
   .routingStrategy(req==null||req.getRoutingStrategy()==null?null:req.getRoutingStrategy().name())
   .maskedPrompt(prompt).latencyMs(latency).providerLatencyMs(providerLatency).errorCategory(category).build());
 }
 @Override @Transactional
 public void recordBlocked(UUID id,AuthenticationContext auth,AIRequest req,String prompt,long latency,long providerLatency,String category){
  if(!personal(auth))return;
  repository.save(PersonalRequestHistory.builder().requestId(id).personalAccountId(auth.getPersonalAccountId())
   .status(PersonalRequestStatus.BLOCKED).provider(req==null?null:req.getProvider()).model(req==null?null:req.getModel())
   .billingMode(req==null?null:req.getBillingMode())
   .routingStrategy(req==null||req.getRoutingStrategy()==null?null:req.getRoutingStrategy().name())
   .maskedPrompt(prompt).latencyMs(latency).providerLatencyMs(providerLatency).errorCategory(category).build());
 }
 @Override @Transactional(readOnly=true)
 public Page<PersonalRequestHistoryResponse> list(UUID accountId,Pageable pageable){
  return repository.findByPersonalAccountIdOrderByCreatedAtDesc(accountId,pageable).map(this::toResponse);
 }
 @Override @Transactional(readOnly=true)
 public PersonalRequestHistoryResponse get(UUID accountId,UUID requestId){
  return repository.findByRequestIdAndPersonalAccountId(requestId,accountId).map(this::toResponse)
   .orElseThrow(()->new BusinessException("Personal request not found."));
 }
 @Override @Transactional(readOnly=true)
 public PersonalUsageSummaryResponse summary(UUID accountId){
  LocalDateTime today=LocalDate.now().atStartOfDay(), month=YearMonth.now().atDay(1).atStartOfDay();
  long todayCount=repository.countByPersonalAccountIdAndCreatedAtGreaterThanEqual(accountId,today);
  long success=repository.countByPersonalAccountIdAndStatusAndCreatedAtGreaterThanEqual(accountId,PersonalRequestStatus.SUCCESS,today);
  long failed=repository.countByPersonalAccountIdAndStatusAndCreatedAtGreaterThanEqual(accountId,PersonalRequestStatus.FAILED,today);
  long monthCount=repository.countByPersonalAccountIdAndCreatedAtGreaterThanEqual(accountId,month);
  Object[] a=firstRow(repository.aggregateSince(accountId,PersonalRequestStatus.SUCCESS,month));
  Object[] o=firstRow(repository.optimizationSummary(accountId,month));
  return PersonalUsageSummaryResponse.builder().requestsToday(todayCount).successfulRequestsToday(success)
   .failedRequestsToday(failed).requestsThisMonth(monthCount).inputTokensThisMonth(num(a,0))
   .outputTokensThisMonth(num(a,1)).totalTokensThisMonth(num(a,2))
   .costThisMonth(decimal(a,3))
   .contextTokensSavedThisMonth(num(o,0)).cacheHitsThisMonth(num(o,1))
   .quotaRequestsRemainingToday(dailyRemaining(accountId))
   .quotaTokensRemainingThisMonth(monthlyRemaining(accountId)).build();
 }
 private long dailyRemaining(UUID accountId){
  if(quotaProperties.getRequestsPerDay()<=0)return 0L;
  var row=quotaRepository.findByPersonalAccountIdAndPeriodTypeAndPeriodStart(
    accountId,QuotaPeriodType.DAILY,LocalDate.now()).orElse(null);
  long used=row==null?0:row.getRequestCount();
  return Math.max(0L,quotaProperties.getRequestsPerDay()-used);
 }
 private long monthlyRemaining(UUID accountId){
  if(quotaProperties.getMonthlyTokenQuota()<=0)return 0L;
  var row=quotaRepository.findByPersonalAccountIdAndPeriodTypeAndPeriodStart(
    accountId,QuotaPeriodType.MONTHLY,YearMonth.now().atDay(1)).orElse(null);
  long used=row==null?0:row.getTokenCount();
  return Math.max(0L,quotaProperties.getMonthlyTokenQuota()-used);
 }
 private Object[] firstRow(java.util.List<Object[]> rows){
  return rows==null||rows.isEmpty()||rows.get(0)==null?new Object[0]:rows.get(0);
 }
 private long num(Object[] a,int i){
  if(a==null||a.length<=i||a[i]==null)return 0L;
  Object value=a[i];
  if(value instanceof Number)return ((Number)value).longValue();
  try{return new java.math.BigDecimal(value.toString()).longValue();}
  catch(NumberFormatException ex){return 0L;}
 }
 private BigDecimal decimal(Object[] a,int i){
  if(a==null||a.length<=i||a[i]==null)return BigDecimal.ZERO;
  Object value=a[i];
  if(value instanceof BigDecimal)return (BigDecimal)value;
  if(value instanceof Number)return BigDecimal.valueOf(((Number)value).doubleValue());
  try{return new BigDecimal(value.toString());}
  catch(NumberFormatException ex){return BigDecimal.ZERO;}
 }
 private boolean personal(AuthenticationContext a){return a!=null&&a.isPersonalPrincipal()&&a.getPersonalAccountId()!=null;}
 private PersonalRequestHistoryResponse toResponse(PersonalRequestHistory h){
  return PersonalRequestHistoryResponse.builder().requestId(h.getRequestId()).status(h.getStatus())
   .provider(h.getProvider()).model(h.getModel()).billingMode(h.getBillingMode()).routingStrategy(h.getRoutingStrategy())
   .maskedPrompt(h.getMaskedPrompt()).maskedResponse(h.getMaskedResponse()).inputTokens(h.getInputTokens())
   .outputTokens(h.getOutputTokens()).totalTokens(h.getTotalTokens()).estimatedInputTokens(h.getEstimatedInputTokens())
   .estimatedOptimizedTokens(h.getEstimatedOptimizedTokens()).estimatedTokensSaved(h.getEstimatedTokensSaved())
   .contextWindowTokens(h.getContextWindowTokens()).latencyMs(h.getLatencyMs()).providerLatencyMs(h.getProviderLatencyMs())
   .cost(h.getCost()).cacheHit(h.isCacheHit()).ragEnabled(h.isRagEnabled()).errorCategory(h.getErrorCategory())
   .createdAt(h.getCreatedAt()).build();
 }
}
