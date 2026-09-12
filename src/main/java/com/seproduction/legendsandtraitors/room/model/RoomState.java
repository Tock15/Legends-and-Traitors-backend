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
     * Optimistic-concurrency stamp, reserved for the conditional writes planned in LT-27.
     *
     * <p>It is carried through the stored document so the mechanism can be added without a schema
     * change, but <strong>nothing increments or checks it yet</strong> — the repository's save is
     * an unconditional overwrite. Do not treat a read-back version as a lock, and do not assume a
     * save failed because the version moved.
     */
    private long version;
}
