package com.ai.gateway.entitlement.util;

import com.ai.gateway.entitlement.enums.Feature;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public final class FeatureUtil {

    private FeatureUtil() {
    }

    public static Set<Feature> parse(
            String features) {

        if (features == null || features.isBlank()) {
            return Set.of();
        }

        return Arrays.stream(
                        features.split(","))
                .map(String::trim)
                .map(Feature::valueOf)
                .collect(Collectors.toSet());

    }

    public static String serialize(
            Set<Feature> features) {

        return features.stream()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining(","));

    }

}
