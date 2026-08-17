package com.ai.gateway.config;

import com.ai.gateway.tenant.TenantContext;
import com.ai.gateway.tenant.TenantSchemaContext;
import org.slf4j.MDC;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Executor for post-provider persistence/observability work.
 *
 * Tenant identity, tenant schema and MDC are explicitly propagated because
 * these values are ThreadLocal/MDC based and must not be lost when work moves
 * off the HTTP request thread.
 */
@Configuration
@EnableAsync
public class GatewayAsyncConfig {

    @Bean(name = "gatewayAsyncExecutor")
    public Executor gatewayAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("gateway-async-");
        executor.setTaskDecorator(new TenantContextTaskDecorator());
        executor.initialize();
        return executor;
    }

    @Bean
    public AsyncUncaughtExceptionHandler gatewayAsyncExceptionHandler() {
        return new GatewayAsyncExceptionHandler();
    }

    private static final class TenantContextTaskDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable runnable) {
            UUID tenantId = TenantContext.get();
            String schema = TenantSchemaContext.get();
            Map<String, String> mdc = MDC.getCopyOfContextMap();

            /*
             * TenantContext/TenantSchemaContext are application ThreadLocals,
             * while AuthorizationService resolves the authenticated principal
             * from Spring Security's SecurityContextHolder. Async execution
             * moves to a different worker thread, so the security context must
             * be explicitly captured and restored alongside the tenant context.
             *
             * Capture the Authentication rather than reusing the mutable
             * SecurityContext instance. A fresh context is installed on the
             * worker thread and the previous worker context is restored in the
             * finally block to prevent cross-request principal leakage.
             */
            Authentication authentication =
                    SecurityContextHolder.getContext().getAuthentication();

            return () -> {
                UUID previousTenant = TenantContext.get();
                String previousSchema = TenantSchemaContext.get();
                Map<String, String> previousMdc = MDC.getCopyOfContextMap();
                SecurityContext previousSecurityContext =
                        SecurityContextHolder.getContext();

                try {
                    if (tenantId != null) {
                        TenantContext.set(tenantId);
                    } else {
                        TenantContext.clear();
                    }

                    if (schema != null) {
                        TenantSchemaContext.set(
                                tenantId,
                                schema);
                    } else {
                        TenantSchemaContext.clear();
                    }

                    if (mdc == null) {
                        MDC.clear();
                    } else {
                        MDC.setContextMap(mdc);
                    }

                    SecurityContext asyncSecurityContext =
                            SecurityContextHolder.createEmptyContext();
                    asyncSecurityContext.setAuthentication(authentication);
                    SecurityContextHolder.setContext(asyncSecurityContext);

                    runnable.run();
                } finally {
                    if (previousTenant != null) {
                        TenantContext.set(previousTenant);
                    } else {
                        TenantContext.clear();
                    }

                    if (previousSchema != null) {
                        TenantSchemaContext.set(
                                previousTenant,
                                previousSchema);
                    } else {
                        TenantSchemaContext.clear();
                    }

                    if (previousMdc == null) {
                        MDC.clear();
                    } else {
                        MDC.setContextMap(previousMdc);
                    }

                    SecurityContextHolder.setContext(previousSecurityContext);
                }
            };
        }
    }

    private static final class GatewayAsyncExceptionHandler
            implements AsyncUncaughtExceptionHandler {

        @Override
        public void handleUncaughtException(
                Throwable ex,
                Method method,
                Object... params) {
            org.slf4j.LoggerFactory.getLogger(GatewayAsyncConfig.class)
                    .error("Async gateway persistence failed: method={}",
                            method.getName(), ex);
        }
    }
}
