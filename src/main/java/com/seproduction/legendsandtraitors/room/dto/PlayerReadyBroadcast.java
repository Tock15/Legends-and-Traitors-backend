package com.seproduction.legendsandtraitors.room.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PlayerReadyBroadcast(
        String event,
        String playerId,
        @JsonProperty("isReady") boolean isReady,
        @JsonProperty("canStartGame") boolean canStartGame) {

    public static final String EVENT = "PLAYER_READY_CHANGED";

    public PlayerReadyBroadcast(String playerId, boolean isReady, boolean canStartGame) {
        this(EVENT, playerId, isReady, canStartGame);
    }
}
