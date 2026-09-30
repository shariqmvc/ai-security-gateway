package com.ai.gateway.core.provider.xai;

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
public class XaiProvider extends NativeChatCompletionsProvider {
    @Value("${xai.base-url:https://api.x.ai/v1}") private String baseUrl;
    @Value("${xai.api-key:}") private String apiKey;
    @Value("${xai.model:grok-4.6}") private String model;

    public XaiProvider(ObjectMapper mapper, PerformanceLogger logger,
                       ProviderCredentialResolver credentials,
                       ProviderHttpProperties httpProperties) {
        super(new RestTemplate(new ProviderHttpRequestFactory(
                Provider.XAI, httpProperties.forProvider(Provider.XAI))),
                mapper, logger, credentials);
    }
    @Override public Provider provider(){return Provider.XAI;}
    @Override protected String baseUrl(){return baseUrl;}
    @Override protected String configuredApiKey(){return apiKey;}
    @Override protected String configuredModel(){return model;}
}
