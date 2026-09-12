package com.seproduction.legendsandtraitors.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class RedisConfig {

    /**
     * Codec shared by every Redis-backed feature repository, which pair it with the
     * auto-configured {@code StringRedisTemplate}.
     *
     * <p>Registering a typed {@code RedisTemplate} per domain here instead would drag every
     * feature entity into global config and turn this class into a hub (ADR-001 §4.4/§4.5).
     *
     * <p>Not exposed as a bare {@code ObjectMapper}/{@code JsonMapper} bean on purpose: either
     * type would compete with — or back off — Spring Boot's auto-configured primary HTTP mapper,
     * whose date handling differs from the format below.
     */
    @Bean
    public RedisJsonCodec redisJsonCodec() {
        return new RedisJsonCodec(redisObjectMapper());
    }

    private ObjectMapper redisObjectMapper() {
        return JsonMapper.builder()
                .enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DateTimeFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                // without this, an integer timestamp is read back as epoch seconds, not millis
                .disable(DateTimeFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }
}
