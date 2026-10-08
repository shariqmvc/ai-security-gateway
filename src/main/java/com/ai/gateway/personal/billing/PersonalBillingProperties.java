package com.ai.gateway.personal.billing;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.Set;
import java.math.BigDecimal;

@Getter @Setter
@ConfigurationProperties(prefix = "alroute.personal.billing")
public class PersonalBillingProperties {
    private Set<String> freeModels = new HashSet<>();
    private int reserveOutputTokens = 4096;
    private BigDecimal reservationMultiplier = new BigDecimal("1.25");
    private BigDecimal minimumCreditCharge = new BigDecimal("0.01");
    /** Monthly provider compute-cost allowance for FREE model execution; 0 means unlimited. */
    private BigDecimal monthlyFreeComputeCreditCap = BigDecimal.ZERO;
    /** Delay between durable credit reservation recovery runs. */
    private long reservationRecoveryIntervalMs = 60000;
    /** Minimum age before a reservation with no provider invocation can be released. */
    private long staleReservationAgeMinutes = 30;
    /** Age after which in-flight reservations are surfaced for manual reconciliation, never auto-released. */
    private long staleInFlightReservationAuditAgeHours = 2;
}
