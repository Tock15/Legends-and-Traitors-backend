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

    /**
     * Claims a room code for a brand-new room: one atomic write that stores the document and sets
     * its TTL only while the code is free.
     *
     * <p>The only safe way to create a room — {@link #save} would overwrite a room another host
     * already owns under the same code.
     *
     * @return {@code false} when the code was already taken, leaving the stored room untouched
     * @throws IllegalArgumentException if {@code room} is null or carries no room code
     */
    boolean saveIfAbsent(RoomState room);

    /**
     * Writes the whole room and resets its TTL in one atomic step, but only while the stored
     * document still carries {@code expectedVersion} — the optimistic-concurrency write the lobby
     * mutation handlers use instead of {@link #save}.
     *
     * <p>Never changes {@code room}'s own version: the caller bumps it before writing.
     *
     * @return {@code false} when the stored version moved or no room is stored, leaving Redis untouched
     * @throws IllegalArgumentException if {@code room} is null or carries no room code
     */
    boolean saveIfVersion(RoomState room, long expectedVersion);

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

    /**
     * @return whether {@code playerId} holds an unexpired kick ban for {@code roomCode}
     *         (key {@code room:{roomCode}:banned:{playerId}}, written by the kick flow)
     */
    boolean isBanned(String roomCode, String playerId);
}
