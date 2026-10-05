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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataRedisTest
@ActiveProfiles("test")
@Import({RedisConfig.class, RoomRedisRepository.class, ApplicationPropertiesConfig.class})
class RoomRedisRepositoryTest {

    private static final String ROOM_CODE = "WXYZ89";
    private static final String KEY = "room:" + ROOM_CODE;
    private static final String BANNED_PLAYER = "guest_114205";
    private static final String BAN_KEY = KEY + ":banned:" + BANNED_PLAYER;
    private static final Instant CREATED_AT = Instant.parse("2026-09-08T14:30:00Z");
    private static final long CREATED_AT_MILLIS = 1788877800000L;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private GameRoomProperties gameRoomProperties;

    @AfterEach
    void clearRoomKeys() {
        stringRedisTemplate.delete(List.of(KEY, BAN_KEY));
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
                .version(3)
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
                .contains("\"version\":3")
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
    @DisplayName("Should claim a free code, storing the room under the configured TTL")
    void shouldClaimFreeCode() {
        boolean claimed = roomRepository.saveIfAbsent(sampleRoom());

        assertThat(claimed).isTrue();
        assertThat(roomRepository.findByCode(ROOM_CODE)).hasValue(sampleRoom());
        assertThat(storedTtlSeconds())
                .isGreaterThan(gameRoomProperties.getTtlSeconds() - 60)
                .isLessThanOrEqualTo(gameRoomProperties.getTtlSeconds());
    }

    @Test
    @DisplayName("Should refuse a taken code without touching the room already stored")
    void shouldRefuseTakenCode() {
        roomRepository.save(sampleRoom());
        String jsonBeforeClaim = storedJson();
        RoomState otherHostRoom = sampleRoom();
        otherHostRoom.setHostId("guest_111111");

        boolean claimed = roomRepository.saveIfAbsent(otherHostRoom);

        assertThat(claimed).isFalse();
        assertThat(storedJson()).isEqualTo(jsonBeforeClaim);
    }

    @Test
    @DisplayName("Should write and reset the TTL when the stored version still matches")
    void shouldSaveWhenVersionMatches() {
        roomRepository.save(sampleRoom());
        stringRedisTemplate.expire(KEY, Duration.ofSeconds(5));
        RoomState updated = sampleRoom();
        updated.setHostId("guest_111111");
        updated.setVersion(4);

        boolean written = roomRepository.saveIfVersion(updated, 3);

        assertThat(written).isTrue();
        assertThat(roomRepository.findByCode(ROOM_CODE)).hasValue(updated);
        assertThat(storedTtlSeconds())
                .isGreaterThan(gameRoomProperties.getTtlSeconds() - 60)
                .isLessThanOrEqualTo(gameRoomProperties.getTtlSeconds());
    }

    @Test
    @DisplayName("Should refuse a write whose expected version has moved, leaving the stored room untouched")
    void shouldRefuseWhenVersionMoved() {
        roomRepository.save(sampleRoom());
        String jsonBeforeWrite = storedJson();
        RoomState stale = sampleRoom();
        stale.setHostId("guest_111111");
        stale.setVersion(3);

        boolean written = roomRepository.saveIfVersion(stale, 2);

        assertThat(written).isFalse();
        assertThat(storedJson()).isEqualTo(jsonBeforeWrite);
    }

    @Test
    @DisplayName("Should refuse a versioned write to a room that is not stored, without creating it")
    void shouldRefuseVersionedWriteToMissingRoom() {
        assertThat(roomRepository.saveIfVersion(sampleRoom(), 3)).isFalse();

        assertThat(roomRepository.existsByCode(ROOM_CODE)).isFalse();
    }

    @Test
    @DisplayName("Should report a ban only while room:{roomCode}:banned:{playerId} exists")
    void shouldReportBanFromBanKey() {
        assertThat(roomRepository.isBanned(ROOM_CODE, BANNED_PLAYER)).isFalse();

        stringRedisTemplate.opsForValue().set(BAN_KEY, "1", Duration.ofMinutes(5));

        assertThat(roomRepository.isBanned(ROOM_CODE, BANNED_PLAYER)).isTrue();
        assertThat(roomRepository.isBanned(ROOM_CODE, "guest_222222")).isFalse();
        assertThat(roomRepository.isBanned("ABCD23", BANNED_PLAYER)).isFalse();
    }

    @Test
    @DisplayName("Should reject a null or blank room code on a ban check with IllegalArgumentException")
    void shouldRejectBlankRoomCodeOnBanCheck() {
        assertThatThrownBy(() -> roomRepository.isBanned(null, BANNED_PLAYER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> roomRepository.isBanned(" ", BANNED_PLAYER))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
