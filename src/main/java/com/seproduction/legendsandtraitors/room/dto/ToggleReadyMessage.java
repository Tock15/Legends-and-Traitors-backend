package com.seproduction.legendsandtraitors.room.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record ToggleReadyMessage(
        @NotNull(message = "isReady must not be null.")
        @JsonProperty("isReady")
        Boolean isReady) {
}
