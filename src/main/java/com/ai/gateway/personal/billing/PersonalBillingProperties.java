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
}
