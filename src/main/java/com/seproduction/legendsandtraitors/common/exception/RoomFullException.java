package com.seproduction.legendsandtraitors.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a player attempts to join a room that has already reached its maximum player capacity.
 * Responds with HTTP 409 CONFLICT and error code "ROOM_FULL".
 *
 * Related Ticket: LT-33 / BE-15
 */
public class RoomFullException extends BaseGameException {

    public static final String ERROR_CODE = "ROOM_FULL";

    private final int maxPlayers;

    public RoomFullException(String message, int maxPlayers) {
        super(HttpStatus.CONFLICT, ERROR_CODE, "Room Full", message);
        this.maxPlayers = maxPlayers;
    }

    public static RoomFullException forRoom(String roomCode, int maxPlayers) {
        return new RoomFullException("Lobby " + roomCode + " has reached its maximum capacity of " + maxPlayers + " players.", maxPlayers);
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }
}
