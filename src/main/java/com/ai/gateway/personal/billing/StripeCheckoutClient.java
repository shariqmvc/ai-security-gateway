package com.ai.gateway.personal.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class StripeCheckoutClient {
 private final ObjectMapper mapper;
 private final PersonalPaymentProperties properties;

 public Session createCheckoutSession(PersonalPaymentIntent intent){
  requireConfigured();
  MultiValueMap<String,String> form=new LinkedMultiValueMap<>();
  form.add("mode","payment");
  form.add("success_url",properties.getSuccessUrl()+"?topup={CHECKOUT_SESSION_ID}");
  form.add("cancel_url",properties.getCancelUrl()+"?topup=canceled&intent="+intent.getId());
  form.add("client_reference_id",intent.getId().toString());
  form.add("line_items[0][price_data][currency]",properties.getCurrency().toLowerCase());
  form.add("line_items[0][price_data][unit_amount]",Long.toString(minor(intent.getBaseAmount())));
  form.add("line_items[0][price_data][product_data][name]","AIRouter Credits");
  form.add("line_items[0][price_data][product_data][description]",intent.getCredits().stripTrailingZeros().toPlainString()+" credits");
  form.add("line_items[0][quantity]","1");
  if(intent.getFeeAmount()!=null && intent.getFeeAmount().signum()>0){
   form.add("line_items[1][price_data][currency]",properties.getCurrency().toLowerCase());
   form.add("line_items[1][price_data][unit_amount]",Long.toString(minor(intent.getFeeAmount())));
   form.add("line_items[1][price_data][product_data][name]","AIRouter platform fee");
   form.add("line_items[1][price_data][product_data][description]","5.5% platform fee, $0.80 minimum");
   form.add("line_items[1][quantity]","1");
  }
  form.add("invoice_creation[enabled]",Boolean.toString(properties.isInvoiceCreationEnabled()));
  form.add("metadata[intent_id]",intent.getId().toString());
  form.add("metadata[personal_account_id]",intent.getPersonalAccountId().toString());
  form.add("metadata[package_code]",intent.getPackageCode());
  form.add("metadata[credits]",intent.getCredits().toPlainString());
  form.add("metadata[fee_amount]",intent.getFeeAmount().toPlainString());
  form.add("payment_intent_data[metadata][intent_id]",intent.getId().toString());

  JsonNode root=post("/v1/checkout/sessions",form,intent.getId().toString());
  return new Session(text(root,"id"),text(root,"url"),text(root,"payment_intent"),text(root,"invoice"));
 }

 public JsonNode retrieve(String path){return get(path);}
 public record Session(String id,String url,String paymentIntentId,String invoiceId){}

 private JsonNode post(String path,MultiValueMap<String,String> form,String idempotency){
  return RestClient.builder().baseUrl(properties.getApiBaseUrl()).build().post().uri(path)
    .header(HttpHeaders.AUTHORIZATION,basicAuth(properties.getSecretKey()))
    .header("Idempotency-Key",idempotency)
    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(JsonNode.class);
 }
 private JsonNode get(String path){
  return RestClient.builder().baseUrl(properties.getApiBaseUrl()).build().get().uri(path)
    .header(HttpHeaders.AUTHORIZATION,basicAuth(properties.getSecretKey())).retrieve().body(JsonNode.class);
 }
 private String basicAuth(String secret){return "Basic "+Base64.getEncoder().encodeToString((secret+":").getBytes(StandardCharsets.UTF_8));}
 private long minor(BigDecimal amount){return amount.movePointRight(2).longValueExact();}
 private String text(JsonNode n,String field){JsonNode v=n==null?null:n.get(field);return v==null||v.isNull()?null:v.asText();}
 private void requireConfigured(){if(properties.getSecretKey()==null||properties.getSecretKey().isBlank())throw new IllegalStateException("Stripe secret key is not configured.");}
}