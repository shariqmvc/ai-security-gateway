package com.ai.gateway.tenant;

import com.ai.gateway.exception.TenantAlreadyExistsException;
import com.ai.gateway.provisioning.TenantProvisioningService;
import com.ai.gateway.tenant.dto.TenantRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TenantServiceImpl
        implements TenantService {

    private final TenantRepository repository;

    private final TenantProvisioningService
            tenantProvisioningService;

    @Override
    public Optional<Tenant> findByTenantCode(
            String tenantCode) {

        return repository.findByTenantCode(
                tenantCode);
    }

    @Override
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

        if (repository.findByTenantCode(request.getTenantCode()).isPresent()) {
            throw new TenantAlreadyExistsException(
                    request.getTenantCode());
        }

        /*
         * Tenant creation establishes the complete tenant
         * identity and default routing configuration.
         *
         * A newly created tenant starts in REQUESTED state and is
         * activated only after the provisioning workflow succeeds.
         */
        Tenant tenant =
                Tenant.builder()
                        .tenantCode(request.getTenantCode())
                        .tenantName(request.getTenantName())
                        .status(TenantStatus.REQUESTED)
                        .type(request.getType())
                        .plan(request.getPlan())
                        .defaultProvider(request.getDefaultProvider())
                        .defaultModel(request.getDefaultModel())
                        .createdAt(LocalDateTime.now())
                        .build();

        Tenant saved =
                repository.save(tenant);

        tenantProvisioningService.provision(
                saved.getId());

        return repository.findById(saved.getId())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Tenant disappeared during provisioning: "
                                        + saved.getId()));
    }
}