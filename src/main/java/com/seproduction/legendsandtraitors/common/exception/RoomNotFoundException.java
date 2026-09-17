package com.seproduction.legendsandtraitors.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested game room cannot be found by its room code.
 * Responds with HTTP 404 NOT FOUND and error code "ROOM_NOT_FOUND".
 *
 * Related Ticket: LT-33 / BE-15
 */
public class RoomNotFoundException extends BaseGameException {

    public static final String ERROR_CODE = "ROOM_NOT_FOUND";

    public RoomNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, ERROR_CODE, "Room Not Found", message);
    }

    public static RoomNotFoundException forRoomCode(String roomCode) {
        return new RoomNotFoundException("Room with code '" + roomCode + "' was not found.");
    }
}
