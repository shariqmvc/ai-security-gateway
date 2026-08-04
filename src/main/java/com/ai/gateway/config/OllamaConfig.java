package com.ai.gateway.config;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "ollama")
@Getter
@Setter
public class OllamaConfig {

    private String model;

    private Base base = new Base();

    @Getter
    @Setter
    public static class Base {
        private String url;
    }
}
