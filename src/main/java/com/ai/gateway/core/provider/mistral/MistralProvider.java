package com.ai.gateway.core.provider.mistral;

import com.ai.gateway.config.ProviderHttpProperties;
import com.ai.gateway.config.ProviderHttpRequestFactory;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.observability.PerformanceLogger;
import com.ai.gateway.core.provider.openai_compatible.NativeChatCompletionsProvider;
import com.ai.gateway.core.provider.ProviderCredentialResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class MistralProvider extends NativeChatCompletionsProvider {
    @Value("${mistral.base-url:https://api.mistral.ai/v1}") private String baseUrl;
    @Value("${mistral.api-key:}") private String apiKey;
    @Value("${mistral.model:mistral-small-latest}") private String model;

    public MistralProvider(ObjectMapper mapper, PerformanceLogger logger,
                           ProviderCredentialResolver credentials,
                           ProviderHttpProperties httpProperties) {
        super(new RestTemplate(new ProviderHttpRequestFactory(
                Provider.MISTRAL, httpProperties.forProvider(Provider.MISTRAL))),
                mapper, logger, credentials);
    }

    @Override public Provider provider(){ return Provider.MISTRAL; }
    @Override protected String baseUrl(){ return baseUrl; }
    @Override protected String configuredApiKey(){ return apiKey; }
    @Override protected String configuredModel(){ return model; }
}
