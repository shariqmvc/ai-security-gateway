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

        if (repository.findByTenantCode(
                request.getTenantCode()).isPresent()) {

            throw new IllegalStateException(
                    "Tenant already exists: "
                            + request.getTenantCode());
        }

        Tenant tenant =
                Tenant.builder()
                        .tenantCode(
                                request.getTenantCode())
                        .tenantName(
                                request.getTenantName())
                        .plan(
                                request.getPlan())
                        .createdAt(
                                LocalDateTime.now())
                        .build();

        Tenant saved =
                repository.save(tenant);

        entitlementProvisioningService.provision(
                saved.getId());

        return saved;
    }
}