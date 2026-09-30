package com.ai.gateway.personal.billing;

import com.ai.gateway.authentication.AuthenticationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PersonalBillingSettingsService {
 private final PersonalBillingSettingsRepository repository;

 @Transactional
 public PersonalBillingSettings get(AuthenticationContext context){
  return repository.findByPersonalAccountId(requireAccount(context)).orElseGet(()->repository.save(PersonalBillingSettings.builder()
    .personalAccountId(context.getPersonalAccountId()).updatedAt(LocalDateTime.now()).build()));
 }

 @Transactional
 public PersonalBillingSettings update(AuthenticationContext context, UpdateRequest request){
  var account=requireAccount(context);
  var settings=get(context);
  if(request.lowBalanceAlertEnabled()!=null) settings.setLowBalanceAlertEnabled(request.lowBalanceAlertEnabled());
  if(request.lowBalanceThreshold()!=null) settings.setLowBalanceThreshold(validateNonNegative(request.lowBalanceThreshold(),"lowBalanceThreshold"));
  if(request.autoTopUpEnabled()!=null) settings.setAutoTopUpEnabled(request.autoTopUpEnabled());
  if(request.autoTopUpAmount()!=null) settings.setAutoTopUpAmount(validatePositive(request.autoTopUpAmount(),"autoTopUpAmount"));
  if(request.monthlySpendCap()!=null) settings.setMonthlySpendCap(request.monthlySpendCap().compareTo(BigDecimal.ZERO)==0?null:validatePositive(request.monthlySpendCap(),"monthlySpendCap"));
  settings.setUpdatedAt(LocalDateTime.now());
  return repository.save(settings);
 }
 private java.util.UUID requireAccount(AuthenticationContext c){
  if(c==null||!c.isPersonalPrincipal()||c.getPersonalAccountId()==null) throw new AccessDeniedException("Personal authentication is required.");
  return c.getPersonalAccountId();
 }
 private BigDecimal validatePositive(BigDecimal v,String n){if(v==null||v.signum()<=0)throw new IllegalArgumentException(n+" must be greater than zero.");return v;}
 private BigDecimal validateNonNegative(BigDecimal v,String n){if(v==null||v.signum()<0)throw new IllegalArgumentException(n+" cannot be negative.");return v;}
 public record UpdateRequest(Boolean lowBalanceAlertEnabled,BigDecimal lowBalanceThreshold,Boolean autoTopUpEnabled,BigDecimal autoTopUpAmount,BigDecimal monthlySpendCap){}
}