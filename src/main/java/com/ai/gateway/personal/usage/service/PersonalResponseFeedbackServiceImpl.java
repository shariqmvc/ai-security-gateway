package com.ai.gateway.personal.usage.service;

import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.personal.usage.entity.PersonalRequestFeedback;
import com.ai.gateway.personal.usage.entity.PersonalResponseRating;
import com.ai.gateway.personal.usage.repository.PersonalRequestFeedbackRepository;
import com.ai.gateway.personal.usage.repository.PersonalRequestHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service @RequiredArgsConstructor
public class PersonalResponseFeedbackServiceImpl implements PersonalResponseFeedbackService {
 private final PersonalRequestFeedbackRepository feedbackRepository;
 private final PersonalRequestHistoryRepository requestRepository;

 @Override @Transactional
 public void rate(UUID accountId, UUID requestId, PersonalResponseRating rating){
  if(requestRepository.findByRequestIdAndPersonalAccountId(requestId,accountId).isEmpty())
   throw new BusinessException("Personal request not found.");
  PersonalRequestFeedback feedback=feedbackRepository.findByRequestIdAndPersonalAccountId(requestId,accountId)
   .orElseGet(()->PersonalRequestFeedback.builder().requestId(requestId).personalAccountId(accountId).build());
  feedback.setRating(rating);
  feedbackRepository.save(feedback);
 }

 @Override @Transactional
 public void remove(UUID accountId, UUID requestId){
  feedbackRepository.findByRequestIdAndPersonalAccountId(requestId,accountId)
   .ifPresent(feedbackRepository::delete);
 }
}
