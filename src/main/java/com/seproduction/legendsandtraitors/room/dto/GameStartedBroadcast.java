package com.seproduction.legendsandtraitors.room.dto;

import com.seproduction.legendsandtraitors.room.model.RoomState;

import java.time.Instant;

public record GameStartedBroadcast(
        String event,
        String roomCode,
        String status,
        int playerCount,
        Instant startedAt) {

    public static final String EVENT = "GAME_STARTED";

    public GameStartedBroadcast(String roomCode, String status, int playerCount, Instant startedAt) {
        this(EVENT, roomCode, status, playerCount, startedAt);
    }

    public static GameStartedBroadcast from(RoomState room) {
        return new GameStartedBroadcast(room.getRoomCode(), room.getStatus().name(), room.getPlayers().size(),
                room.getStartedAt());
    }
}
