package com.autoparts.inventory.store;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;

@Component
public class RedisCache {
    private final StringRedisTemplate redis;

    public RedisCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String get(String key) {
        return redis.opsForValue().get(key);
    }

    public void set(String key, String value, Duration ttl) {
        redis.opsForValue().set(key, value, ttl);
    }

    public long incr(String key) {
        Long v = redis.opsForValue().increment(key);
        return v == null ? 0 : v;
    }

    public void expire(String key, Duration ttl) {
        redis.expire(key, ttl);
    }

    public void delete(String... keys) {
        redis.delete(Arrays.asList(keys));
    }

    public boolean exists(String key) {
        Boolean v = redis.hasKey(key);
        return Boolean.TRUE.equals(v);
    }

    public boolean ping() {
        try {
            var factory = redis.getConnectionFactory();
            if (factory == null) {
                return false;
            }
            try (var connection = factory.getConnection()) {
                return "PONG".equalsIgnoreCase(connection.ping());
            }
        } catch (Exception e) {
            return false;
        }
    }
}
