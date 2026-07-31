package com.ai.gateway.provider;

import com.ai.gateway.enums.Provider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class AIProviderFactory {
    private final Map<Provider, AIProvider> providers =
            new EnumMap<>(Provider.class);

    public AIProviderFactory(List<AIProvider> implementations) {

        implementations.forEach(provider ->
                providers.put(provider.provider(), provider));

    }

    public AIProvider getProvider(Provider provider) {

        AIProvider implementation =
                providers.get(provider);

        if (implementation == null) {

            throw new IllegalArgumentException(
                    "Unsupported Provider : " + provider);

        }

        return implementation;

    }
}
