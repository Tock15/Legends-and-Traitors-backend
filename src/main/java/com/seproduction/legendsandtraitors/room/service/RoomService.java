package com.seproduction.legendsandtraitors.room.service;

import com.seproduction.legendsandtraitors.common.exception.GameAlreadyStartedException;
import com.seproduction.legendsandtraitors.common.exception.InvalidRequestException;
import com.seproduction.legendsandtraitors.common.exception.PlayerBannedException;
import com.seproduction.legendsandtraitors.common.exception.RoomFullException;
import com.seproduction.legendsandtraitors.common.exception.RoomNotFoundException;
import com.seproduction.legendsandtraitors.config.GameRoomProperties;
import com.seproduction.legendsandtraitors.room.model.CreateRoomRequest;
import com.seproduction.legendsandtraitors.room.model.CreateRoomResponse;
import com.seproduction.legendsandtraitors.room.model.PlayerSlot;
import com.seproduction.legendsandtraitors.room.model.RoleSettings;
import com.seproduction.legendsandtraitors.room.model.RoomState;
import com.seproduction.legendsandtraitors.room.model.RoomStatus;
import com.seproduction.legendsandtraitors.room.repository.RoomRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class RoomService {

    private static final int MAX_CLAIM_ATTEMPTS = 3;
    private static final int MAX_JOIN_ATTEMPTS = 3;

    private final RoomCodeGenerator roomCodeGenerator;
    private final RoomRepository roomRepository;
    private final GameRoomProperties gameRoomProperties;
    private final Clock clock;

    @Autowired
    RoomService(RoomCodeGenerator roomCodeGenerator,
                RoomRepository roomRepository,
                GameRoomProperties gameRoomProperties) {
        this(roomCodeGenerator, roomRepository, gameRoomProperties, Clock.systemUTC());
    }

    RoomService(RoomCodeGenerator roomCodeGenerator,
                RoomRepository roomRepository,
                GameRoomProperties gameRoomProperties,
                Clock clock) {
        this.roomCodeGenerator = roomCodeGenerator;
        this.roomRepository = roomRepository;
        this.gameRoomProperties = gameRoomProperties;
        this.clock = clock;
    }

    /**
     * Creates a {@code LOBBY} room with the caller seated as a ready host; a null request or
     * {@code maxPlayers} takes the configured default.
     */
    public CreateRoomResponse createRoom(String hostUserId, String hostDisplayName, CreateRoomRequest request) {
        Assert.hasText(hostUserId, "hostUserId must not be blank");
        Assert.hasText(hostDisplayName, "hostDisplayName must not be blank");

        int maxPlayers = resolveMaxPlayers(request);
        // Redis stores instants as epoch millis, so anything finer would only exist in this response.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);

        for (int attempt = 0; attempt < MAX_CLAIM_ATTEMPTS; attempt++) {
            String roomCode = roomCodeGenerator.generateUniqueRoomCode();
            if (roomRepository.saveIfAbsent(newRoom(roomCode, hostUserId, hostDisplayName, maxPlayers, now))) {
                return new CreateRoomResponse(roomCode, joinUrl(roomCode), hostUserId, now);
            }
        }
        throw new IllegalStateException("Unable to claim a unique room code");
    }

    /**
     * Seats a player, or idempotently re-activates the slot they already hold (AFK or still
     * connected), which skips the capacity check. A lost write is retried on a fresh read up to
     * {@value #MAX_JOIN_ATTEMPTS} times.
     */
    public RoomState joinRoom(String roomCode, String playerId, String displayName, String color) {
        Assert.hasText(roomCode, "roomCode must not be blank");
        Assert.hasText(playerId, "playerId must not be blank");
        Assert.hasText(displayName, "displayName must not be blank");

        String code = roomCode.toUpperCase(Locale.ROOT);
        for (int attempt = 0; attempt < MAX_JOIN_ATTEMPTS; attempt++) {
            RoomState room = roomRepository.findByCode(code)
                    .orElseThrow(() -> RoomNotFoundException.forRoomCode(code));
            validateJoin(code, room, playerId);

            Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
            seat(room, playerId, displayName, color, now);
            room.setLastActiveAt(now);

            long readVersion = room.getVersion();
            room.setVersion(readVersion + 1);
            if (roomRepository.saveIfVersion(room, readVersion)) {
                return room;
            }
        }
        throw new IllegalStateException(
                "Unable to join room " + code + " after " + MAX_JOIN_ATTEMPTS + " conflicting writes");
    }

    private void validateJoin(String code, RoomState room, String playerId) {
        if (room.getStatus() != RoomStatus.LOBBY) {
            throw GameAlreadyStartedException.forRoom(code);
        }
        if (slotOf(room, playerId).isEmpty() && room.getPlayers().size() >= room.getMaxPlayers()) {
            throw RoomFullException.forRoom(code, room.getMaxPlayers());
        }
        if (roomRepository.isBanned(code, playerId)) {
            throw PlayerBannedException.forPlayer(playerId, code);
        }
    }

    private static Optional<PlayerSlot> slotOf(RoomState room, String playerId) {
        return room.getPlayers().stream()
                .filter(slot -> playerId.equals(slot.getId()))
                .findFirst();
    }

    private static void seat(RoomState room, String playerId, String displayName, String color, Instant now) {
        slotOf(room, playerId).ifPresentOrElse(slot -> {
            slot.setAfk(false);
            slot.setLastActiveAt(now);
            if (slot.getColor() == null) {
                slot.setColor(color);
            }
        }, () -> room.getPlayers().add(PlayerSlot.builder()
                .id(playerId)
                .displayName(displayName)
                .host(false)
                .ready(false)
                .afk(false)
                .color(color)
                .joinedAt(now)
                .lastActiveAt(now)
                .build()));
    }

    private int resolveMaxPlayers(CreateRoomRequest request) {
        if (request == null || request.maxPlayers() == null) {
            return gameRoomProperties.getMaxPlayers();
        }

        int minCapacity = gameRoomProperties.getMinCapacity();
        int maxCapacity = gameRoomProperties.getMaxCapacity();
        int requested = request.maxPlayers();
        if (requested < minCapacity || requested > maxCapacity) {
            throw new InvalidRequestException("maxPlayers must be between " + minCapacity + " and " + maxCapacity);
        }
        return requested;
    }

    private RoomState newRoom(String roomCode, String hostUserId, String hostDisplayName,
                              int maxPlayers, Instant now) {
        PlayerSlot hostSlot = PlayerSlot.builder()
                .id(hostUserId)
                .displayName(hostDisplayName)
                .host(true)
                .ready(true)
                .afk(false)
                .joinedAt(now)
                .lastActiveAt(now)
                .build();

        return RoomState.builder()
                .roomCode(roomCode)
                .status(RoomStatus.LOBBY)
                .hostId(hostUserId)
                .maxPlayers(maxPlayers)
                // Mutable on purpose: the lobby join handler appends to this roster.
                .players(new ArrayList<>(List.of(hostSlot)))
                .settings(defaultRoleSettings())
                .createdAt(now)
                .lastActiveAt(now)
                .build();
    }

    /** A 4-player spread the host re-balances once the roster fills; the King is always exactly one. */
    private static RoleSettings defaultRoleSettings() {
        return RoleSettings.builder().king(1).loyalist(1).rebel(1).spy(1).build();
    }

    private String joinUrl(String roomCode) {
        String joinBaseUrl = gameRoomProperties.getJoinBaseUrl();
        Assert.hasText(joinBaseUrl, "game.room.join-base-url must be configured");
        return StringUtils.trimTrailingCharacter(joinBaseUrl, '/') + "/lobby/" + roomCode;
    }
}
