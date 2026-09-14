package com.ai.gateway.core.context;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "gateway.context.optimization")
public class ContextOptimizationProperties {

	private boolean enabled = true;
	private int maxInputTokens = 16000;
	private int minInputTokens = 2000;
	private int minimumSavingsTokens = 128;
	private int prefixTokens = 2000;
	private int suffixTokens = 5000;
	private int maxSegments = 400;
}
