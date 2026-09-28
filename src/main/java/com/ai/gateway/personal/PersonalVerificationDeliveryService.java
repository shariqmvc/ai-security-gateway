package com.ai.gateway.personal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class PersonalVerificationDeliveryService {
    private final ObjectProvider<JavaMailSender> mailSender;
    @Value("${alroute.personal.auth.verification-base-url:http://localhost:5173/verify-email}") private String verificationBaseUrl;
    @Value("${alroute.personal.auth.mail-from:noreply@airouter.local}") private String mailFrom;
    @Value("${alroute.personal.auth.sms.provider:none}") private String smsProvider;
    @Value("${alroute.personal.auth.sms.twilio.account-sid:}") private String twilioAccountSid;
    @Value("${alroute.personal.auth.sms.twilio.auth-token:}") private String twilioAuthToken;
    @Value("${alroute.personal.auth.sms.twilio.from:}") private String twilioFrom;

    public void sendEmailVerification(String email, String token) {
        String link=verificationBaseUrl+"?token="+enc(token)+"&email="+enc(email);
        JavaMailSender sender=mailSender.getIfAvailable();
        if(sender==null){log.warn("Personal email delivery is not configured; verification link generated for {}",email);return;}
        SimpleMailMessage message=new SimpleMailMessage();
        message.setFrom(mailFrom);message.setTo(email);message.setSubject("Verify your AIRouter Personal account");
        message.setText("Verify your AIRouter Personal account:\n\n"+link+"\n\nThis link expires according to your account verification policy.");
        sender.send(message);
    }

    public void sendPhoneVerification(String phoneNumber,String code) {
        if(!"TWILIO".equalsIgnoreCase(smsProvider)||twilioAccountSid.isBlank()||twilioAuthToken.isBlank()||twilioFrom.isBlank()){
            log.warn("Personal SMS delivery is not configured for phone ending {}",maskPhone(phoneNumber));return;
        }
        String auth=java.util.Base64.getEncoder().encodeToString((twilioAccountSid+":"+twilioAuthToken).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        RestClient.create("https://api.twilio.com").post().uri("/2010-04-01/Accounts/{sid}/Messages.json",twilioAccountSid)
            .header("Authorization","Basic "+auth).header("Content-Type","application/x-www-form-urlencoded")
            .body("To="+enc(phoneNumber)+"&From="+enc(twilioFrom)+"&Body="+enc("Your AIRouter verification code is "+code+". It expires soon."))
            .retrieve().toBodilessEntity();
    }
    private String enc(String value){return java.net.URLEncoder.encode(value,java.nio.charset.StandardCharsets.UTF_8);}
    private String maskPhone(String phone){if(phone==null||phone.length()<4)return "****";return "****"+phone.substring(phone.length()-4);}
}