package com.seproduction.legendsandtraitors.room.repository;

import com.seproduction.legendsandtraitors.config.GameRoomProperties;
import com.seproduction.legendsandtraitors.config.RedisJsonCodec;
import com.seproduction.legendsandtraitors.room.model.RoomState;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RoomRedisRepository implements RoomRepository {

    private static final String KEY_PREFIX = "room:";
    private static final String BAN_KEY_INFIX = ":banned:";

    private static final RedisScript<Long> SAVE_IF_VERSION = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if not current then return 0 end
            if cjson.decode(current).version ~= tonumber(ARGV[2]) then return 0 end
            redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[3])
            return 1
            """, Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisJsonCodec redisJsonCodec;
    private final GameRoomProperties gameRoomProperties;

    @Override
    public void save(RoomState room) {
        Assert.notNull(room, "room must not be null");
        Assert.hasText(room.getRoomCode(), "roomCode must not be empty");
        stringRedisTemplate.opsForValue().set(key(room.getRoomCode()), redisJsonCodec.encode(room), ttl());
    }

    @Override
    public boolean saveIfAbsent(RoomState room) {
        Assert.notNull(room, "room must not be null");
        Assert.hasText(room.getRoomCode(), "roomCode must not be empty");
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                .setIfAbsent(key(room.getRoomCode()), redisJsonCodec.encode(room), ttl()));
    }

    @Override
    public boolean saveIfVersion(RoomState room, long expectedVersion) {
        Assert.notNull(room, "room must not be null");
        Assert.hasText(room.getRoomCode(), "roomCode must not be empty");
        Long written = stringRedisTemplate.execute(SAVE_IF_VERSION, List.of(key(room.getRoomCode())),
                redisJsonCodec.encode(room), Long.toString(expectedVersion),
                Long.toString(gameRoomProperties.getTtlSeconds()));
        return Long.valueOf(1).equals(written);
    }

    @Override
    public Optional<RoomState> findByCode(String roomCode) {
        return Optional.ofNullable(stringRedisTemplate.opsForValue().get(key(roomCode)))
                .map(json -> redisJsonCodec.decode(json, RoomState.class));
    }

    @Override
    public boolean touch(String roomCode) {
        return Boolean.TRUE.equals(stringRedisTemplate.expire(key(roomCode), ttl()));
    }

    @Override
    public boolean existsByCode(String roomCode) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key(roomCode)));
    }

    @Override
    public boolean deleteByCode(String roomCode) {
        return Boolean.TRUE.equals(stringRedisTemplate.delete(key(roomCode)));
    }

    @Override
    public boolean isBanned(String roomCode, String playerId) {
        Assert.hasText(playerId, "playerId must not be empty");
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key(roomCode) + BAN_KEY_INFIX + playerId));
    }

    private Duration ttl() {
        return Duration.ofSeconds(gameRoomProperties.getTtlSeconds());
    }

    private String key(String roomCode) {
        Assert.hasText(roomCode, "roomCode must not be empty");
        return KEY_PREFIX + roomCode;
    }
}
