package com.ai.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "ollama")
@Getter
@Setter
public class OllamaConfig {

    /**
     * Default model used when a request does not explicitly select a model.
     * The default is also kept as the first model in the configured catalog.
     */
    private String model;

    /**
     * Models exposed by this Ollama deployment.
     *
     * The default model is automatically included by the model registry even
     * when this list is empty, preserving backward compatibility.
     */
    private List<String> models = new ArrayList<>();

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
