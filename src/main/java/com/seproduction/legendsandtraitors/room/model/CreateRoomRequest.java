package com.seproduction.legendsandtraitors.room.model;

/**
 * @param maxPlayers requested capacity, or null for the configured default
 */
public record CreateRoomRequest(Integer maxPlayers) {
}
