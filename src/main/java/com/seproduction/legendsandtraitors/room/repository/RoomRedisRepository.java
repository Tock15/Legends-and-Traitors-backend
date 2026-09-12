package com.seproduction.legendsandtraitors.room.repository;

import com.seproduction.legendsandtraitors.config.GameRoomProperties;
import com.seproduction.legendsandtraitors.room.model.RoomState;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RoomRedisRepository implements RoomRepository {

    private static final String KEY_PREFIX = "room:";

    private final RedisTemplate<String, RoomState> roomRedisTemplate;
    private final GameRoomProperties gameRoomProperties;

    @Override
    public void save(RoomState room) {
        Assert.notNull(room, "room must not be null");
        Assert.hasText(room.getRoomCode(), "roomCode must not be empty");
        roomRedisTemplate.opsForValue().set(key(room.getRoomCode()), room, ttl());
    }

    @Override
    public Optional<RoomState> findByCode(String roomCode) {
        return Optional.ofNullable(roomRedisTemplate.opsForValue().get(key(roomCode)));
    }

    @Override
    public boolean touch(String roomCode) {
        return Boolean.TRUE.equals(roomRedisTemplate.expire(key(roomCode), ttl()));
    }

    @Override
    public boolean existsByCode(String roomCode) {
        return Boolean.TRUE.equals(roomRedisTemplate.hasKey(key(roomCode)));
    }

    @Override
    public boolean deleteByCode(String roomCode) {
        return Boolean.TRUE.equals(roomRedisTemplate.delete(key(roomCode)));
    }

    private Duration ttl() {
        return Duration.ofSeconds(gameRoomProperties.getTtlSeconds());
    }

    private String key(String roomCode) {
        Assert.hasText(roomCode, "roomCode must not be empty");
        return KEY_PREFIX + roomCode;
    }
}
