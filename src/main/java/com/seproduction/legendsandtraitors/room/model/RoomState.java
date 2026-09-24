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

    // No default on purpose: a baked-in 8 would silently cap a premium room.
    private int maxPlayers;

    @Builder.Default
    private List<PlayerSlot> players = new ArrayList<>();

    private RoleSettings settings;

    private Instant createdAt;

    private Instant lastActiveAt;

    // Checked by RoomRepository#saveIfVersion only; plain save neither checks nor advances it.
    private long version;
}
