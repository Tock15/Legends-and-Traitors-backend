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
     * Smallest capacity a client may request when creating a room (default: 4).
     *
     * <p>Not the same rule as {@code minPlayers}, which gates start eligibility and drops to 2 in
     * dev/test: the capacity bounds are deliberately left uniform across every profile.
     */
    private int minCapacity = 4;

    /**
     * Largest capacity a client may request when creating a room (default: 10).
     *
     * <p>Only the premium tier is meant to reach 10, but nothing reads {@code isPremium} yet, so
     * any authenticated caller can request the ceiling.
     */
    private int maxCapacity = 10;

    /**
     * Frontend origin the shareable lobby invite URL is built on (default: the Vite dev server).
     */
    private String joinBaseUrl = "http://localhost:5173";

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
