package com.project.distributed_rate_limiter.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class RateLimiterService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // The atomic Token Bucket algorithm written in Lua
    private static final String TOKEN_BUCKET_SCRIPT =
            "local key = KEYS[1] " +
                    "local capacity = tonumber(ARGV[1]) " +
                    "local refill_rate = tonumber(ARGV[2]) " + // Tokens refilled per millisecond
                    "local current_time = tonumber(ARGV[3]) " + // Current system time in ms
                    "local ttl = tonumber(ARGV[4]) " +          // Key expiration safety window

                    // 1. Fetch current bucket tracking metrics from the Redis Hash
                    "local bucket = redis.call('hmget', key, 'tokens', 'last_updated') " +
                    "local tokens = tonumber(bucket[1]) " +
                    "local last_updated = tonumber(bucket[2]) " +

                    // 2. If the bucket doesn't exist yet, initialize it full
                    "if not tokens then " +
                    "    tokens = capacity " +
                    "    last_updated = current_time " +
                    "else " +
                    // 3. Lazy Evaluation Math: Refill tokens based on time passed
                    "    local time_passed = current_time - last_updated " +
                    "    if time_passed > 0 then " +
                    "        local generated_tokens = time_passed * refill_rate " +
                    "        tokens = math.min(capacity, tokens + generated_tokens) " +
                    "        last_updated = current_time " +
                    "    end " +
                    "end " +

                    // 4. Evaluation and Enforcement Block
                    "if tokens >= 1 then " +
                    "    tokens = tokens - 1 " + // Consume exactly 1 token
                    "    redis.call('hset', key, 'tokens', tokens, 'last_updated', last_updated) " +
                    "    redis.call('expire', key, ttl) " + // Reset key TTL expiration
                    "    return 1 " + // Access Granted
                    "else " +
                    "    return 0 " + // Access Blocked (Rate Limit Exceeded) +
                    "end";

    public boolean isAllowed(String key, long capacity, long windowInSeconds) {
        // Calculate precision refill rate: total tokens / total window time in milliseconds
        double refillRate = (double) capacity / (windowInSeconds * 1000);

        long currentTimeMillis = System.currentTimeMillis();
        long ttlInSeconds = windowInSeconds * 2; // Keep metadata alive double the window time safely

        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(TOKEN_BUCKET_SCRIPT);
        redisScript.setResultType(Long.class);

        // Execute script atomically in Redis
        Long result = redisTemplate.execute(
                redisScript,
                Collections.singletonList(key),
                String.valueOf(capacity),
                String.valueOf(refillRate),
                String.valueOf(currentTimeMillis),
                String.valueOf(ttlInSeconds)
        );

        return result != null && result == 1;
    }
}
