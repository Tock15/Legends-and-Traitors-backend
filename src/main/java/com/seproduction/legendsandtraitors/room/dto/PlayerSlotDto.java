package com.seproduction.legendsandtraitors.room.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.seproduction.legendsandtraitors.room.model.PlayerSlot;

public record PlayerSlotDto(
        String id,
        String displayName,
        @JsonProperty("isHost") boolean isHost,
        @JsonProperty("isReady") boolean isReady,
        @JsonProperty("isAfk") boolean isAfk,
        String color) {

    public static PlayerSlotDto from(PlayerSlot slot) {
        return new PlayerSlotDto(slot.getId(), slot.getDisplayName(), slot.isHost(), slot.isReady(),
                slot.isAfk(), slot.getColor());
    }
}
