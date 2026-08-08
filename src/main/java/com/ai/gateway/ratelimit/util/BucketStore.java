package com.ai.gateway.ratelimit.util;

import com.ai.gateway.ratelimit.dto.TokenBucket;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class BucketStore {

    private final ConcurrentMap<String, TokenBucket> buckets =
            new ConcurrentHashMap<>();

    public TokenBucket get(String key) {
        return buckets.get(key);
    }

    public void put(String key, TokenBucket bucket) {
        buckets.put(key, bucket);
    }
}
