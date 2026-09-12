package com.seproduction.legendsandtraitors.room.repository;

import com.seproduction.legendsandtraitors.config.ApplicationPropertiesConfig;
import com.seproduction.legendsandtraitors.config.GameRoomProperties;
import com.seproduction.legendsandtraitors.config.RedisConfig;
import com.seproduction.legendsandtraitors.room.model.PlayerSlot;
import com.seproduction.legendsandtraitors.room.model.RoleSettings;
import com.seproduction.legendsandtraitors.room.model.RoomState;
import com.seproduction.legendsandtraitors.room.model.RoomStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataRedisTest
@ActiveProfiles("test")
@Import({RedisConfig.class, RoomRedisRepository.class, ApplicationPropertiesConfig.class})
class RoomRedisRepositoryTest {

    private static final String ROOM_CODE = "WXYZ89";
    private static final String KEY = "room:" + ROOM_CODE;
    private static final Instant CREATED_AT = Instant.parse("2026-09-08T14:30:00Z");
    private static final long CREATED_AT_MILLIS = 1788877800000L;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private GameRoomProperties gameRoomProperties;

    @AfterEach
    void clearRoomKey() {
        stringRedisTemplate.delete(KEY);
    }

    private RoomState sampleRoom() {
        return RoomState.builder()
                .roomCode(ROOM_CODE)
                .status(RoomStatus.LOBBY)
                .hostId("guest_948201")
                .maxPlayers(8)
                .players(List.of(PlayerSlot.builder()
                        .id("guest_948201")
                        .displayName("Guest948201")
                        .host(true)
                        .ready(true)
                        .afk(false)
                        .color("#E53E3E")
                        .joinedAt(CREATED_AT)
                        .lastActiveAt(CREATED_AT)
                        .build()))
                .settings(RoleSettings.builder().king(1).loyalist(1).rebel(2).spy(0).build())
                .createdAt(CREATED_AT)
                .lastActiveAt(CREATED_AT)
                .build();
    }

    private String storedJson() {
        return stringRedisTemplate.opsForValue().get(KEY);
    }

    private Long storedTtlSeconds() {
        return stringRedisTemplate.getExpire(KEY, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should round-trip a room including nested players, settings and enum status")
    void shouldRoundTripRoomState() {
        RoomState room = sampleRoom();

        roomRepository.save(room);

        assertThat(roomRepository.findByCode(ROOM_CODE)).hasValue(room);
    }

    @Test
    @DisplayName("Should store the room as a plain JSON string under room:{roomCode} with no type hint")
    void shouldStoreRoomAsPlainJsonString() {
        roomRepository.save(sampleRoom());

        assertThat(stringRedisTemplate.type(KEY)).isEqualTo(DataType.STRING);
        assertThat(storedJson())
                .startsWith("{")
                .contains("\"roomCode\":\"WXYZ89\"")
                .contains("\"status\":\"LOBBY\"")
                .contains("\"hostId\":\"guest_948201\"")
                .contains("\"maxPlayers\":8")
                .contains("\"settings\":{")
                .contains("\"king\":1")
                .contains("\"loyalist\":1")
                .contains("\"rebel\":2")
                .contains("\"spy\":0")
                .doesNotContain("@class");
    }

    @Test
    @DisplayName("Should serialize boolean flags under their frontend contract names")
    void shouldSerializeBooleanFlagsUsingContractNames() {
        roomRepository.save(sampleRoom());

        assertThat(storedJson())
                .contains("\"isHost\":true")
                .contains("\"isReady\":true")
                .contains("\"isAfk\":false")
                .doesNotContain("\"host\":")
                .doesNotContain("\"ready\":")
                .doesNotContain("\"afk\":");
    }

    @Test
    @DisplayName("Should store instants as epoch milliseconds")
    void shouldStoreTimestampsAsEpochMillis() {
        roomRepository.save(sampleRoom());

        assertThat(storedJson())
                .contains("\"createdAt\":" + CREATED_AT_MILLIS)
                .contains("\"joinedAt\":" + CREATED_AT_MILLIS);
    }

    @Test
    @DisplayName("Should truncate sub-millisecond instant precision on round-trip")
    void shouldTruncateSubMillisecondPrecision() {
        Instant preciseInstant = Instant.parse("2026-09-08T14:30:00.123456789Z");
        RoomState room = sampleRoom();
        room.setCreatedAt(preciseInstant);

        roomRepository.save(room);

        assertThat(roomRepository.findByCode(ROOM_CODE))
                .get()
                .extracting(RoomState::getCreatedAt)
                .isEqualTo(preciseInstant.truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    @DisplayName("Should apply the configured TTL on save")
    void shouldApplyConfiguredTtlOnSave() {
        roomRepository.save(sampleRoom());

        assertThat(storedTtlSeconds())
                .isGreaterThan(gameRoomProperties.getTtlSeconds() - 60)
                .isLessThanOrEqualTo(gameRoomProperties.getTtlSeconds());
    }

    @Test
    @DisplayName("Should reset a decayed TTL on every save")
    void shouldResetTtlOnSubsequentSave() {
        roomRepository.save(sampleRoom());
        stringRedisTemplate.expire(KEY, Duration.ofSeconds(5));

        roomRepository.save(sampleRoom());

        assertThat(storedTtlSeconds()).isGreaterThan(gameRoomProperties.getTtlSeconds() - 60);
    }

    @Test
    @DisplayName("Should refresh the TTL on touch without rewriting the document")
    void shouldRefreshTtlOnTouch() {
        roomRepository.save(sampleRoom());
        stringRedisTemplate.expire(KEY, Duration.ofSeconds(5));
        String jsonBeforeTouch = storedJson();

        boolean touched = roomRepository.touch(ROOM_CODE);

        assertThat(touched).isTrue();
        assertThat(storedTtlSeconds()).isGreaterThan(gameRoomProperties.getTtlSeconds() - 60);
        assertThat(storedJson()).isEqualTo(jsonBeforeTouch);
    }

    @Test
    @DisplayName("Should report a failed touch for an unknown room")
    void shouldNotTouchUnknownRoom() {
        assertThat(roomRepository.touch("NOPE01")).isFalse();
    }

    @Test
    @DisplayName("Should report existence and delete a stored room")
    void shouldReportExistenceAndDelete() {
        roomRepository.save(sampleRoom());
        assertThat(roomRepository.existsByCode(ROOM_CODE)).isTrue();

        assertThat(roomRepository.deleteByCode(ROOM_CODE)).isTrue();

        assertThat(roomRepository.existsByCode(ROOM_CODE)).isFalse();
        assertThat(roomRepository.findByCode(ROOM_CODE)).isEmpty();
    }

    @Test
    @DisplayName("Should return an empty optional for an unknown room")
    void shouldReturnEmptyForUnknownRoom() {
        assertThat(roomRepository.findByCode("NOPE01")).isEqualTo(Optional.empty());
    }

    @Test
    @DisplayName("Should default maxPlayers to 8 when the builder does not set it")
    void shouldDefaultMaxPlayersToEight() {
        assertThat(RoomState.builder().roomCode(ROOM_CODE).build().getMaxPlayers()).isEqualTo(8);
    }
}
