package com.seproduction.legendsandtraitors.room.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerSlot {

    private String id;

    private String displayName;

    @JsonProperty("isHost")
    private boolean host;

    @JsonProperty("isReady")
    private boolean ready;

    @JsonProperty("isAfk")
    private boolean afk;

    private String color;

    private Instant joinedAt;

    private Instant lastActiveAt;
}
