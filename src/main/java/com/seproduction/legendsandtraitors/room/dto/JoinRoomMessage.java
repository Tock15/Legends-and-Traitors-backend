package com.seproduction.legendsandtraitors.room.dto;

import jakarta.validation.constraints.Pattern;

/**
 * @param color optional; a rejoin only uses it to fill a slot that has no colour yet
 */
public record JoinRoomMessage(@Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color) {
}
