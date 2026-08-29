package com.ai.gateway.config;

import com.ai.gateway.core.model.Provider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean("openAiRestClient")
    public RestClient openAiRestClient(
            ProviderHttpProperties properties) {
        return RestClient.builder()
                .requestFactory(
                        new ProviderHttpRequestFactory(
                                Provider.OPENAI,
                                properties.forProvider(Provider.OPENAI)))
                .build();
    }
}
