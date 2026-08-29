package com.ai.gateway.personal.billing;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PersonalBillingProperties.class)
public class PersonalBillingConfiguration { }
