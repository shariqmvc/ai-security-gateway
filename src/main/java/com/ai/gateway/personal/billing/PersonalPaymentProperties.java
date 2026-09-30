package com.ai.gateway.personal.billing;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter @Setter
@ConfigurationProperties(prefix="alroute.personal.payment")
public class PersonalPaymentProperties {
 private String webhookSecret="";
 private String secretKey="";
 private String apiBaseUrl="https://api.stripe.com";
 private String provider="STRIPE";
 private String currency="USD";
 private String successUrl="http://localhost:5173/billing";
 private String cancelUrl="http://localhost:5173/billing";
 private BigDecimal platformFeeRate=BigDecimal.valueOf(0.055);
 private BigDecimal minimumPlatformFee=BigDecimal.valueOf(0.80);
 private boolean invoiceCreationEnabled=true;
 private BigDecimal customMinimumCredits=BigDecimal.TEN;
 private BigDecimal customMaximumCredits=BigDecimal.valueOf(1000);
 private String creditsPolicy="Credits do not expire.";
 private String refundPolicy="Refunds are handled under AIRouter's published refund policy.";
 private String taxPolicy="USD billing; taxes are determined by the configured payment/tax setup.";
 private Map<String,CreditPackage> packages=new LinkedHashMap<>();
 public record CreditPackage(BigDecimal amount,BigDecimal credits){}
 public BigDecimal feeFor(BigDecimal base){
  BigDecimal percentage=base.multiply(platformFeeRate);
  return percentage.max(minimumPlatformFee).setScale(2,java.math.RoundingMode.HALF_UP);
 }
}