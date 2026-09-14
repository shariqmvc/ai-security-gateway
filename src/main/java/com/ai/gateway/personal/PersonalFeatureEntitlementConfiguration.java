package com.ai.gateway.personal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PersonalFeatureEntitlementProperties.class)
public class PersonalFeatureEntitlementConfiguration { }
