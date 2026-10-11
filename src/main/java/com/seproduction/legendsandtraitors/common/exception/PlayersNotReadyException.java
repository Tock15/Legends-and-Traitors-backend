package com.seproduction.legendsandtraitors.common.exception;

import org.springframework.http.HttpStatus;

public class PlayersNotReadyException extends BaseGameException {

    public static final String ERROR_CODE = "PLAYERS_NOT_READY";

    public PlayersNotReadyException(String message) {
        super(HttpStatus.CONFLICT, ERROR_CODE, "Players Not Ready", message);
    }

    public static PlayersNotReadyException forRoom(String roomCode) {
        return new PlayersNotReadyException("Lobby " + roomCode + " has players who are not ready.");
    }
}
