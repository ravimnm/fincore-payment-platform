package com.fincore.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class RedisConnectionTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void shouldWriteAndReadFromRedis() {

        String key = "fincore:test:connection";
        String value = "redis-connected";

        redisTemplate.opsForValue().set(key, value);

        String result = redisTemplate.opsForValue().get(key);

        assertEquals(value, result);

        redisTemplate.delete(key);
    }
}