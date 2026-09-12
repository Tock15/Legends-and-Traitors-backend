package com.seproduction.legendsandtraitors.config;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * JSON codec for values stored in Redis as plain strings.
 *
 * <p>Owns the Redis wire format — epoch-millisecond instants, no {@code @class} type hints,
 * unknown properties ignored — without knowing any feature's types: callers hand in their own
 * class. That is what lets {@link RedisConfig} stay domain-agnostic (ADR-001 §4.4) while each
 * feature keeps its own serialization inside its own package.
 *
 * <p>Deliberately separate from the auto-configured HTTP mapper, so that changing the REST
 * contract can never silently rewrite documents already stored in Redis.
 */
public class RedisJsonCodec {

    private final ObjectMapper mapper;

    public RedisJsonCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * @throws JacksonException if {@code value} cannot be serialized
     */
    public String encode(Object value) {
        return mapper.writeValueAsString(value);
    }

    /**
     * @throws JacksonException if {@code json} is malformed or does not fit {@code type}
     */
    public <T> T decode(String json, Class<T> type) {
        return mapper.readValue(json, type);
    }
}
