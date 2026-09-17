package com.seproduction.legendsandtraitors.room.model;

import java.time.Instant;

public record CreateRoomResponse(String roomCode, String joinUrl, String hostId, Instant createdAt) {
}
