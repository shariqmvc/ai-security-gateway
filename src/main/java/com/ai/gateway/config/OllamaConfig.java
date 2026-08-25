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

    /** Maximum context window sent to Ollama for gateway requests. */
    private Integer numCtx = 4096;

    /** Maximum generated tokens. This prevents unbounded local generation latency. */
    private Integer numPredict = 512;

    /** Keep the model resident to avoid repeated model-load latency. */
    private String keepAlive = "10m";

    private Base base = new Base();

    @Getter
    @Setter
    public static class Base {
        private String url;
    }
}
