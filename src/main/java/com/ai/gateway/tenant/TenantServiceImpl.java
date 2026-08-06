package com.ai.gateway.tenant;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TenantServiceImpl
        implements TenantService {

    private final TenantRepository repository;

    @Override
    public Optional<Tenant> findByTenantCode(String tenantCode) {

        return repository.findByTenantCode(tenantCode);

    }

}