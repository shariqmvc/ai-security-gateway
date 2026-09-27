package com.ai.gateway.core.provider.groq;

import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.observability.PerformanceLogger;
import com.ai.gateway.core.provider.openai_compatible.NativeChatCompletionsProvider;
import com.ai.gateway.personal.PersonalProviderCredentialResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class GroqProvider extends NativeChatCompletionsProvider {
    @Value("\${groq.base-url:https://api.groq.com/openai/v1}") private String baseUrl;
    @Value("\${groq.api-key:}") private String apiKey;
    @Value("\${groq.model:openai/gpt-oss-120b}") private String model;
    public GroqProvider(ObjectMapper mapper,PerformanceLogger logger,PersonalProviderCredentialResolver credentials){
        super(new RestTemplate(),mapper,logger,credentials);
    }
    @Override public Provider provider(){return Provider.GROQ;}
    @Override protected String baseUrl(){return baseUrl;}
    @Override protected String configuredApiKey(){return apiKey;}
    @Override protected String configuredModel(){return model;}
}
