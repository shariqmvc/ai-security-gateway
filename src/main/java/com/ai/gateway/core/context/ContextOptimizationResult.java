package com.ai.gateway.core.context;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ContextOptimizationResult {

	String originalContext;
	String optimizedContext;
	int originalTokens;
	int optimizedTokens;
	int tokensSaved;
	int duplicateSegmentsRemoved;
	boolean compressed;
	String strategy;
}
