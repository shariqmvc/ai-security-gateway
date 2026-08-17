package com.ai.gateway.service;

import com.ai.gateway.authentication.AuthenticationService;
import com.ai.gateway.entitlement.enums.Plan;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.ratelimit.service.RateLimiterService;
import com.ai.gateway.security.AuthorizationService;
import com.ai.gateway.tenant.Tenant;
import com.ai.gateway.tenant.TenantController;
import com.ai.gateway.tenant.TenantService;
import com.ai.gateway.tenant.TenantStatus;
import com.ai.gateway.tenant.TenantType;
import com.ai.gateway.tenant.dto.TenantRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.ai.gateway.enums.Provider;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.ai.gateway.ratelimit.filter.RateLimitFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@WebMvcTest(
        value = TenantController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = RateLimitFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
class TenantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TenantService tenantService;

    @MockitoBean
    private AuthenticationService authenticationService;

    @MockitoBean
    private RateLimiterService rateLimiterService;

    @MockitoBean
    private AuthorizationService authorizationService;

    @Test
    void shouldCreateTenant() throws Exception {

        Tenant tenant =
                Tenant.builder()
                        .tenantCode("ACME")
                        .tenantName("ACME Corporation")
                        .status(TenantStatus.ACTIVE)
                        .type(TenantType.STANDARD)
                        .plan(Plan.PROFESSIONAL)
                        .defaultProvider(Provider.GEMINI)
                        .defaultModel("gemini-3.6-flash")
                        .build();

        when(tenantService.create(any(TenantRequest.class)))
                .thenReturn(tenant);

        mockMvc.perform(
                        post("/admin/tenants")
                                .contentType(APPLICATION_JSON)
                                .content("""
                {
                  "tenantCode": "ACME",
                  "tenantName": "ACME Corporation",
                  "plan": "PROFESSIONAL",
                  "type": "STANDARD",
                  "defaultProvider": "GEMINI",
                  "defaultModel": "gemini-3.6-flash"
                }
                """))
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath("$.tenantCode")
                                .value("ACME"))
                .andExpect(
                        jsonPath("$.tenantName")
                                .value("ACME Corporation"))
                .andExpect(
                        jsonPath("$.plan")
                                .value("PROFESSIONAL"))
                .andExpect(
                        jsonPath("$.status")
                                .value("ACTIVE"))
                .andExpect(
                        jsonPath("$.type")
                                .value("STANDARD"))
                .andExpect(
                        jsonPath("$.defaultProvider")
                                .value("GEMINI"))
                .andExpect(
                        jsonPath("$.defaultModel")
                                .value("gemini-3.6-flash"));
    }

    @Test
    void shouldRejectRequestWithoutPlan()
            throws Exception {

        mockMvc.perform(
                        post("/admin/tenants")
                                .contentType(APPLICATION_JSON)
                                .content("""
                                {
                                  "tenantCode": "ACME",
                                  "tenantName": "ACME Corporation"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectRequestWithoutTenantType()
            throws Exception {

        mockMvc.perform(
                        post("/admin/tenants")
                                .contentType(APPLICATION_JSON)
                                .content("""
                            {
                              "tenantCode": "ACME",
                              "tenantName": "ACME Corporation",
                              "plan": "PROFESSIONAL",
                              "defaultProvider": "GEMINI",
                              "defaultModel": "gemini-3.6-flash"
                            }
                            """))
                .andExpect(status().isBadRequest());
    }
    @Test
    void shouldRejectRequestWithoutDefaultProvider()
            throws Exception {

        mockMvc.perform(
                        post("/admin/tenants")
                                .contentType(APPLICATION_JSON)
                                .content("""
                            {
                              "tenantCode": "ACME",
                              "tenantName": "ACME Corporation",
                              "plan": "PROFESSIONAL",
                              "type": "STANDARD",
                              "defaultModel": "gemini-3.6-flash"
                            }
                            """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectRequestWithoutDefaultModel()
            throws Exception {

        mockMvc.perform(
                        post("/admin/tenants")
                                .contentType(APPLICATION_JSON)
                                .content("""
                            {
                              "tenantCode": "ACME",
                              "tenantName": "ACME Corporation",
                              "plan": "PROFESSIONAL",
                              "type": "STANDARD",
                              "defaultProvider": "GEMINI"
                            }
                            """))
                .andExpect(status().isBadRequest());
    }

}
