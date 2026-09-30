package com.ai.gateway.personal.billing;

import com.ai.gateway.personal.repository.PersonalAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PersonalLowBalanceAlertService {
 private final PersonalBillingSettingsRepository settingsRepository;
 private final PersonalAccountRepository accountRepository;
 @Autowired(required=false) private JavaMailSender mailSender;

 public void notifyIfNeeded(UUID accountId,BigDecimal availableBalance){
  if(mailSender==null||accountId==null||availableBalance==null)return;
  var settings=settingsRepository.findByPersonalAccountId(accountId).orElse(null);
  if(settings==null||!settings.isLowBalanceAlertEnabled()||availableBalance.compareTo(settings.getLowBalanceThreshold())>0)return;
  if(settings.getLastLowBalanceAlertAt()!=null&&Duration.between(settings.getLastLowBalanceAlertAt(),LocalDateTime.now()).toHours()<24)return;
  var account=accountRepository.findById(accountId).orElse(null);
  if(account==null||account.getUser()==null||account.getUser().getEmail()==null)return;
  try{
   var message=new SimpleMailMessage();
   message.setTo(account.getUser().getEmail());
   message.setSubject("AIRouter low credit balance");
   message.setText("Your AIRouter credit balance is "+availableBalance+" credits, below your alert threshold of "+settings.getLowBalanceThreshold()+" credits.");
   mailSender.send(message);
   settings.setLastLowBalanceAlertAt(LocalDateTime.now());
   settingsRepository.save(settings);
  }catch(RuntimeException ignored){}
 }
}