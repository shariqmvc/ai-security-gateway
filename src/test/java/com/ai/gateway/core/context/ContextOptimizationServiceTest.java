package com.ai.gateway.core.context;

import org.junit.jupiter.api.Test;

import com.ai.gateway.core.contract.ContextMessage;

import static org.junit.jupiter.api.Assertions.*;

class ContextOptimizationServiceTest {

	@Test
	void leavesSmallContextUntouched() {
		ContextOptimizationProperties properties = new ContextOptimizationProperties();
		ContextOptimizationService service = new ContextOptimizationService(properties);

		ContextOptimizationResult result = service.optimize("hello world");

		assertFalse(result.isCompressed());
		assertEquals("hello world", result.getOptimizedContext());
		assertEquals(0, result.getTokensSaved());
	}

	@Test
	void removesExactDuplicateSegmentsBeforeProviderInvocation() {
		ContextOptimizationProperties properties = new ContextOptimizationProperties();
		properties.setMinInputTokens(1);
		properties.setMaxInputTokens(10000);
		properties.setMinimumSavingsTokens(1);
		ContextOptimizationService service = new ContextOptimizationService(properties);

		String context = "System instructions are important.\n\n"
				+ "The same retrieved fact is repeated.\n\n"
				+ "The same retrieved fact is repeated.\n\n"
				+ "Answer the user.";

		ContextOptimizationResult result = service.optimize(context);

		assertTrue(result.isCompressed());
		assertEquals(1, result.getDuplicateSegmentsRemoved());
		assertTrue(result.getTokensSaved() > 0);
		assertEquals(1, countOccurrences(result.getOptimizedContext(), "The same retrieved fact is repeated."));
	}

	@Test
	void prunesMiddleContextWhenInputBudgetIsExceeded() {
		ContextOptimizationProperties properties = new ContextOptimizationProperties();
		properties.setMinInputTokens(1);
		properties.setMaxInputTokens(120);
		properties.setMinimumSavingsTokens(1);
		properties.setPrefixTokens(20);
		properties.setSuffixTokens(20);
		ContextOptimizationService service = new ContextOptimizationService(properties);

		String context = String.join("\n\n",
				"SYSTEM: Always preserve these instructions.",
				"Older conversation segment A. ".repeat(30),
				"Older conversation segment B. ".repeat(30),
				"Older conversation segment C. ".repeat(30),
				"LATEST USER REQUEST: keep this recent request.");

		ContextOptimizationResult result = service.optimize(context);

		assertTrue(result.isCompressed());
		assertTrue(result.getOptimizedTokens() <= properties.getMaxInputTokens());
		assertTrue(result.getTokensSaved() > 0);
		assertTrue(result.getOptimizedContext().contains("SYSTEM: Always preserve"));
		assertTrue(result.getOptimizedContext().contains("LATEST USER REQUEST"));
		assertTrue(result.getOptimizedContext().contains("Earlier context compressed by AIRouter"));
	}


	@Test
	void preservesSystemAndLatestUserWhilePruningOlderRoleContext() {
		ContextOptimizationProperties properties = new ContextOptimizationProperties();
		properties.setMinInputTokens(1);
		properties.setMinimumSavingsTokens(1);
		ContextOptimizationService service = new ContextOptimizationService(properties);

		java.util.List<ContextMessage> messages = java.util.List.of(
				ContextMessage.builder().role("system").content("Always preserve this system instruction.").build(),
				ContextMessage.builder().role("user").content("Old user context ".repeat(80)).build(),
				ContextMessage.builder().role("assistant").content("Old assistant context ".repeat(80)).build(),
				ContextMessage.builder().role("user").content("Latest user request: preserve this.").build());

		ContextOptimizationResult result = service.optimize(messages, 120);

		assertTrue(result.isCompressed());
		assertTrue(result.getOptimizedTokens() <= 120);
		assertTrue(result.getOptimizedContext().contains("Always preserve this system instruction."));
		assertTrue(result.getOptimizedContext().contains("Latest user request: preserve this."));
		assertTrue(result.getTokensSaved() > 0);
	}

	@Test
	void preservesRolesWhenFlatteningStructuredConversation() {
		ContextOptimizationProperties properties = new ContextOptimizationProperties();
		properties.setEnabled(true);
		properties.setMinInputTokens(0);
		properties.setMinimumSavingsTokens(0);

		ContextOptimizationService service = new ContextOptimizationService(properties);

		ContextOptimizationResult result = service.optimize(
				java.util.List.of(
						ContextMessage.builder().role("user").content("What are the seven heavens?").build(),
						ContextMessage.builder().role("assistant").content("They are described as seven heavens.").build(),
						ContextMessage.builder().role("user").content("Explain the second one.").build()),
				4000);

		assertTrue(result.getOptimizedContext().contains("User:\nWhat are the seven heavens?"));
		assertTrue(result.getOptimizedContext().contains("Assistant:\nThey are described as seven heavens."));
		assertTrue(result.getOptimizedContext().contains("User:\nExplain the second one."));
	}


	@Test
	void usesModelSpecificBudget() {
		ContextOptimizationProperties properties = new ContextOptimizationProperties();
		properties.setMinInputTokens(1);
		properties.setMinimumSavingsTokens(1);
		ContextOptimizationService service = new ContextOptimizationService(properties);

		String context = "system instructions\n\n" + "older context ".repeat(500) + "\n\nlatest request";
		ContextOptimizationResult result = service.optimize(context, 300);

		assertTrue(result.isCompressed());
		assertTrue(result.getOptimizedTokens() <= 300);
		assertTrue(result.getTokensSaved() > 0);
	}

	private int countOccurrences(String value, String needle) {
		int count = 0;
		int index = 0;
		while ((index = value.indexOf(needle, index)) >= 0) {
			count++;
			index += needle.length();
		}
		return count;
	}
}
