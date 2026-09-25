package com.ai.gateway.personal.usage.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name="PERSONAL_REQUEST_FEEDBACK", uniqueConstraints={
 @UniqueConstraint(name="uk_personal_request_feedback_request", columnNames={"personal_account_id","request_id"})
}, indexes={
 @Index(name="idx_personal_feedback_account_created", columnList="personal_account_id,created_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PersonalRequestFeedback {
 @Id @GeneratedValue private UUID id;
 @Column(name="request_id",nullable=false) private UUID requestId;
 @Column(name="personal_account_id",nullable=false) private UUID personalAccountId;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=8) private PersonalResponseRating rating;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
 @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;

 @PrePersist void prePersist(){
  LocalDateTime now=LocalDateTime.now();
  if(createdAt==null)createdAt=now;
  if(updatedAt==null)updatedAt=now;
 }
 @PreUpdate void preUpdate(){updatedAt=LocalDateTime.now();}
}
