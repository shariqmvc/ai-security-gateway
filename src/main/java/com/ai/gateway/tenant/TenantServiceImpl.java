package com.ai.gateway.tenant;

import com.ai.gateway.provisioning.EntitlementProvisioningService;
import com.ai.gateway.tenant.dto.TenantRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TenantServiceImpl
        implements TenantService {

    private final TenantRepository repository;

    private final EntitlementProvisioningService
            entitlementProvisioningService;

    @Override
    public Optional<Tenant> findByTenantCode(
            String tenantCode) {

        return repository.findByTenantCode(
                tenantCode);
    }

    @Override
    @Transactional
    public Tenant create(
            TenantRequest request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Tenant request is required.");
        }

        if (request.getTenantCode() == null
                || request.getTenantCode().isBlank()) {
            throw new IllegalArgumentException(
                    "Tenant code is required.");
        }

        if (request.getTenantName() == null
                || request.getTenantName().isBlank()) {
            throw new IllegalArgumentException(
                    "Tenant name is required.");
        }

        if (request.getPlan() == null) {
            throw new IllegalArgumentException(
                    "Tenant plan is required.");
        }

        if (request.getType() == null) {
            throw new IllegalArgumentException(
                    "Tenant type is required.");
        }

        if (request.getDefaultProvider() == null) {
            throw new IllegalArgumentException(
                    "Tenant default provider is required.");
        }

        if (request.getDefaultModel() == null
                || request.getDefaultModel().isBlank()) {
            throw new IllegalArgumentException(
                    "Tenant default model is required.");
        }

        if (repository.findByTenantCode(
                request.getTenantCode()).isPresent()) {

            throw new IllegalStateException(
                    "Tenant already exists: "
                            + request.getTenantCode());
        }

        /*
         * Tenant creation establishes the complete tenant
         * identity and default routing configuration.
         *
         * A newly provisioned tenant is immediately ACTIVE
         * after successful provisioning.
         */
        Tenant tenant =
                Tenant.builder()
                        .tenantCode(
                                request.getTenantCode())
                        .tenantName(
                                request.getTenantName())
                        .status(
                                TenantStatus.ACTIVE)
                        .type(
                                request.getType())
                        .plan(
                                request.getPlan())
                        .defaultProvider(
                                request.getDefaultProvider())
                        .defaultModel(
                                request.getDefaultModel())
                        .createdAt(
                                LocalDateTime.now())
                        .build();

        Tenant saved =
                repository.save(tenant);

        /*
         * Entitlement provisioning remains part of the
         * same transaction. If provisioning fails, the
         * tenant creation transaction is rolled back.
         */
        entitlementProvisioningService.provision(
                saved.getId());

        return saved;
    }
}