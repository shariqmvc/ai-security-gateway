package com.ai.gateway.personal.file.entity;
import jakarta.persistence.*; import lombok.*; import java.time.LocalDateTime; import java.util.UUID;
@Entity @Table(name="PERSONAL_FILES", indexes={@Index(name="idx_personal_file_account_created", columnList="personal_account_id,created_at")})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PersonalFile {
 @Id @GeneratedValue private UUID id;
 @Column(name="personal_account_id",nullable=false) private UUID personalAccountId;
 @Column(name="original_name",nullable=false,length=255) private String originalName;
 @Column(name="storage_name",nullable=false,length=100) private String storageName;
 @Column(name="content_type",nullable=false,length=100) private String contentType;
 @Column(nullable=false) private Long size;
 @Column(name="sha256",nullable=false,length=64) private String sha256;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
 @PrePersist void prePersist(){if(createdAt==null)createdAt=LocalDateTime.now();}
}
