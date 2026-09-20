package com.seproduction.legendsandtraitors.room.service;

import com.seproduction.legendsandtraitors.common.exception.InvalidRequestException;
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

/**
 * Opens lobby rooms and returns the code and invite URL a host shares.
 */
@Service
public class RoomService {

    private static final int MAX_CLAIM_ATTEMPTS = 3;

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
     * Creates a room in {@code LOBBY} with the caller already seated as a ready host, so the roster
     * is non-empty before any WebSocket connection exists.
     *
     * @param request may be null, as may its {@code maxPlayers} — either means the configured default
     * @return the room code, the shareable invite URL, and the host identity as stored
     * @throws InvalidRequestException if a requested capacity falls outside the configured range
     * @throws IllegalStateException if {@value #MAX_CLAIM_ATTEMPTS} generated codes were all claimed by someone else
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
