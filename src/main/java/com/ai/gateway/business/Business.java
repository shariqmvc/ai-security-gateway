package com.ai.gateway.business;

import com.ai.gateway.tenant.Tenant;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Business is the organizational/commercial identity associated with exactly
 * one technical tenant. Tenant remains the platform security/isolation boundary.
 *
 * The database identity (id) is intentionally separate from the externally
 * addressable businessId, following the internal-PK/public-UUID pattern used
 * by mature enterprise domain models.
 */
@Entity
@Table(
        name = "BUSINESSES",
        indexes = {
                @Index(name = "idx_business_name", columnList = "name"),
                @Index(name = "idx_business_tenant_id", columnList = "tenant_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_business_business_id", columnNames = "business_id"),
                @UniqueConstraint(name = "uk_business_tenant", columnNames = "tenant_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Business {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, unique = true, updatable = false)
    private UUID businessId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "alias_name", length = 255)
    private String aliasName;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "city", length = 120)
    private String city;

    @Column(name = "state_province", length = 120)
    private String stateProvince;

    @Column(name = "country_code", length = 3)
    private String countryCode;

    @Column(name = "zip_code", length = 30)
    private String zipCode;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "contact_email", length = 320)
    private String contactEmail;

    @Column(name = "website_url", length = 500)
    private String websiteUrl;

    @Column(name = "doing_business_as", length = 255)
    private String dba;

    @Column(name = "company_registration_number", length = 120)
    private String companyRegistrationNumber;

    @Column(name = "tax_identifier", length = 120)
    private String taxIdentifier;

    @Column(name = "duns_number", length = 30)
    private String dunsNumber;

    @Column(name = "industry", length = 160)
    private String industry;

    @Column(name = "employee_count_band", length = 80)
    private String employeeCountBand;

    @Column(name = "timezone", length = 80)
    private String timezone;

    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_status", nullable = false, length = 40)
    @Builder.Default
    private BusinessStatus businessStatus = BusinessStatus.REQUESTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_type", nullable = false, length = 40)
    @Builder.Default
    private BusinessType businessType = BusinessType.STANDARD;

    /** Optional parent business for reseller/partner hierarchies. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_business_id")
    private Business parentBusiness;

    /** Technical/security boundary. Exactly one business owns a tenant. */
    @OneToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(
            name = "tenant_id",
            unique = true,
            foreignKey = @ForeignKey(name = "fk_business_tenant"))
    private Tenant tenant;

    @Column(name = "source", length = 80)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (businessId == null) {
            businessId = UUID.randomUUID();
        }
        if (businessStatus == null) {
            businessStatus = BusinessStatus.REQUESTED;
        }
        if (businessType == null) {
            businessType = BusinessType.STANDARD;
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = createdAt;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
