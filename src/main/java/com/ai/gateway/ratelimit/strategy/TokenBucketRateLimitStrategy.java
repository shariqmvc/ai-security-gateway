package com.ai.gateway.ratelimit.strategy;

import com.ai.gateway.ratelimit.dto.RateLimitConfiguration;
import com.ai.gateway.ratelimit.dto.RateLimitResult;
import com.ai.gateway.ratelimit.dto.TokenBucket;
import com.ai.gateway.ratelimit.util.BucketStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TokenBucketRateLimitStrategy
        implements RateLimitStrategy {

    private final BucketStore bucketStore;

    @Override
    public synchronized RateLimitResult allow(
            String key,
            RateLimitConfiguration configuration) {

        long now = System.currentTimeMillis();

        TokenBucket bucket = bucketStore.get(key);

        if (bucket == null) {

            bucket = TokenBucket.builder()
                    .availableTokens(configuration.getCapacity())
                    .lastRefillTimestamp(now)
                    .build();

            bucketStore.put(key, bucket);

        }

        refill(bucket, configuration, now);

        if (bucket.getAvailableTokens() <= 0) {

            long retry =
                    calculateRetryAfter(bucket, configuration);

            return RateLimitResult.builder()
                    .allowed(false)
                    .message("Rate limit exceeded.")
                    .remainingTokens(0)
                    .retryAfterSeconds(retry)
                    .build();

        }

        bucket.setAvailableTokens(
                bucket.getAvailableTokens() - 1);

        return RateLimitResult.builder()
                .allowed(true)
                .remainingTokens(bucket.getAvailableTokens())
                .retryAfterSeconds(0)
                .build();




    }

    private void refill(
            TokenBucket bucket,
            RateLimitConfiguration configuration,
            long now) {

        long elapsed =
                now - bucket.getLastRefillTimestamp();

        long refillInterval =
                configuration.getRefillDurationSeconds() * 1000;

        if (elapsed < refillInterval) {
            return;
        }

        long refillCount =
                elapsed / refillInterval;

        long tokensToAdd =
                refillCount *
                        configuration.getRefillTokens();

        bucket.setAvailableTokens(

                Math.min(

                        configuration.getCapacity(),

                        bucket.getAvailableTokens()
                                + tokensToAdd));

        bucket.setLastRefillTimestamp(now);

    }

    private long calculateRetryAfter(
            TokenBucket bucket,
            RateLimitConfiguration configuration) {

        return configuration.getRefillDurationSeconds();

    }
    }
