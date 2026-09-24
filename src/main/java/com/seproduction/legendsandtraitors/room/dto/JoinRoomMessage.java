package com.seproduction.legendsandtraitors.room.dto;

import jakarta.validation.constraints.Pattern;

public record JoinRoomMessage(
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must be a hex value like #3182CE.") String color) {
}
