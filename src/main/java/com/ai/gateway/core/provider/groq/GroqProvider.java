package com.ai.gateway.core.provider.groq;

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
public class GroqProvider extends NativeChatCompletionsProvider {
    @Value("${groq.base-url:https://api.groq.com/openai/v1}") private String baseUrl;
    @Value("${groq.api-key:}") private String apiKey;
    @Value("${groq.model:openai/gpt-oss-120b}") private String model;

    public GroqProvider(ObjectMapper mapper, PerformanceLogger logger,
                        ProviderCredentialResolver credentials,
                        ProviderHttpProperties httpProperties) {
        super(new RestTemplate(new ProviderHttpRequestFactory(
                Provider.GROQ, httpProperties.forProvider(Provider.GROQ))),
                mapper, logger, credentials);
    }
    @Override public Provider provider(){return Provider.GROQ;}
    @Override protected String baseUrl(){return baseUrl;}
    @Override protected String configuredApiKey(){return apiKey;}
    @Override protected String configuredModel(){return model;}
}
