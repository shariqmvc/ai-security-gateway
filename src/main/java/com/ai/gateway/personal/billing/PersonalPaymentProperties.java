package com.ai.gateway.personal.billing;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter @Setter
@ConfigurationProperties(prefix="alroute.personal.payment")
public class PersonalPaymentProperties {
 private String webhookSecret="";
 private String provider="EXTERNAL";
 private String currency="USD";
 private Map<String, CreditPackage> packages=new LinkedHashMap<>();
 public record CreditPackage(java.math.BigDecimal amount, java.math.BigDecimal credits) {}
}
