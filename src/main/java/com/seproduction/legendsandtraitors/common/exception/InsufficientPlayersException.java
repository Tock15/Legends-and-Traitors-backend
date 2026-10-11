package com.seproduction.legendsandtraitors.common.exception;

import org.springframework.http.HttpStatus;

public class InsufficientPlayersException extends BaseGameException {

    public static final String ERROR_CODE = "MIN_PLAYERS_NOT_MET";

    private final int minPlayers;

    public InsufficientPlayersException(String message, int minPlayers) {
        super(HttpStatus.CONFLICT, ERROR_CODE, "Not Enough Players", message);
        this.minPlayers = minPlayers;
    }

    public static InsufficientPlayersException forRoom(String roomCode, int minPlayers) {
        return new InsufficientPlayersException(
                "Lobby " + roomCode + " needs at least " + minPlayers + " players to start.", minPlayers);
    }

    public int getMinPlayers() {
        return minPlayers;
    }
}
