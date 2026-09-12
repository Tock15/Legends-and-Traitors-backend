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

    @Builder.Default
    private int maxPlayers = 8;

    @Builder.Default
    private List<PlayerSlot> players = new ArrayList<>();

    private RoleSettings settings;

    private Instant createdAt;

    private Instant lastActiveAt;
}
