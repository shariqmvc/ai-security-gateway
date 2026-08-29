package com.ai.gateway.config;

import com.ai.gateway.core.model.Provider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean("geminiRestTemplate")
    public RestTemplate geminiRestTemplate(
            ProviderHttpProperties properties) {
        return new RestTemplate(
                new ProviderHttpRequestFactory(
                        Provider.GEMINI,
                        properties.forProvider(Provider.GEMINI)));
    }

    @Bean("ollamaRestTemplate")
    public RestTemplate ollamaRestTemplate(
            ProviderHttpProperties properties) {
        return new RestTemplate(
                new ProviderHttpRequestFactory(
                        Provider.OLLAMA,
                        properties.forProvider(Provider.OLLAMA)));
    }


}
