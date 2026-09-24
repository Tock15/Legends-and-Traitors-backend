package com.seproduction.legendsandtraitors.room.dto;

/**
 * Error pushed to the failing session only, on {@code /user/queue/errors}.
 */
public record LobbyErrorMessage(String code, String message) {
}
