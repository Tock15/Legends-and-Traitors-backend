package com.seproduction.legendsandtraitors.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserDto(
        String id,
        String displayName,
        @JsonProperty("isGuest") boolean guest,
        @JsonProperty("isPremium") boolean premium) {
}
