package com.seproduction.legendsandtraitors.room.repository;

import com.seproduction.legendsandtraitors.room.model.RoomState;

import java.util.Optional;

/**
 * Persistence for lobby rooms. Rooms are ephemeral: writes reset an idle TTL
 * ({@code game.room.ttl-seconds}), so a lookup cannot tell "expired" from "never existed".
 */
public interface RoomRepository {

    /**
     * Writes the whole room and resets its TTL. Last write wins: it never reads or compares
     * {@code version}, so concurrent read-modify-write cycles on one room lose an update.
     *
     * @throws IllegalArgumentException if {@code room} is null or carries no room code
     */
    void save(RoomState room);

    /** Reads a room without extending its TTL; empty when unknown or expired. */
    Optional<RoomState> findByCode(String roomCode);

    /**
     * Extends the stored room's TTL without rewriting the document, leaving the {@code lastActiveAt}
     * inside it stale between saves.
     *
     * @return {@code false} when no room is stored under {@code roomCode}
     */
    boolean touch(String roomCode);

    /** @return whether a room is currently stored under {@code roomCode} */
    boolean existsByCode(String roomCode);

    /** @return {@code true} when a room was deleted, {@code false} when none was stored */
    boolean deleteByCode(String roomCode);
}
