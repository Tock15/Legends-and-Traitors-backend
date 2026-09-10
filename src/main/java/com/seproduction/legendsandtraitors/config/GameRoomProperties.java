package com.seproduction.legendsandtraitors.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe configuration properties for game room lifecycle and lobby rules.
 * Bound to prefix "game.room" from application YAML profiles.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "game.room")
public class GameRoomProperties {

    /**
     * Number of characters in a generated room code (default: 6).
     */
    private int codeLength = 6;

    /**
     * Minimum ready players required to start a game (default: 4, overridden to 2 in dev).
     */
    private int minPlayers = 4;

    /**
     * Maximum allowed players in a single room/lobby (default: 8).
     */
    private int maxPlayers = 8;

    /**
     * Idle TTL in seconds before Redis deletes an abandoned room session (default: 1800s = 30m).
     */
    private long ttlSeconds = 1800;

    /**
     * Duration in minutes a kicked player is banned from rejoining the room (default: 5m).
     */
    private int kickBanDurationMinutes = 5;

    /**
     * Seconds without WebSocket heartbeat presence before marking a player AFK (default: 60s).
     */
    private int afkThresholdSeconds = 60;
}
