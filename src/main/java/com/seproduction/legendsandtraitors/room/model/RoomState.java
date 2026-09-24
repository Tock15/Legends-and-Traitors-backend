package com.seproduction.legendsandtraitors.room.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomState {

    private String roomCode;

    private RoomStatus status;

    private String hostId;

    /**
     * Capacity of this particular room, stamped at creation from {@code game.room.max-players}
     * (premium rooms raise it to 10).
     *
     * <p>Has no model-level default on purpose: a hardcoded 8 here would silently cap a premium
     * room back to 8 if a caller ever forgot to set it, and the lobby rules are required to read
     * from the injected {@code GameRoomProperties} rather than a baked-in range.
     */
    private int maxPlayers;

    @Builder.Default
    private List<PlayerSlot> players = new ArrayList<>();

    private RoleSettings settings;

    private Instant createdAt;

    private Instant lastActiveAt;

    /**
     * Optimistic-concurrency stamp for the lobby mutation handlers.
     *
     * <p>{@code RoomRepository#saveIfVersion} writes only while the stored value still matches the
     * one the caller read, and the lobby join path bumps it on every such write. The plain
     * {@code save} still ignores it and overwrites unconditionally, so a mutation written through
     * {@code save} neither checks nor advances it.
     */
    private long version;
}
