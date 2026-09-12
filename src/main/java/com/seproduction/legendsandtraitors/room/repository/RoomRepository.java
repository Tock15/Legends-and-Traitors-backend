package com.seproduction.legendsandtraitors.room.repository;

import com.seproduction.legendsandtraitors.room.model.RoomState;

import java.util.Optional;

/**
 * Persistence for lobby rooms.
 *
 * <p>Rooms are ephemeral: every write resets an idle TTL ({@code game.room.ttl-seconds}) and the
 * store drops a room that is neither saved nor touched before it elapses. Every lookup therefore
 * has to treat "expired" and "never existed" as the same answer.
 */
public interface RoomRepository {

    /**
     * Writes the whole room document and resets its idle TTL.
     *
     * <p><strong>Last write wins.</strong> This is an unconditional overwrite: it neither reads
     * nor compares {@code RoomState.version}, so two concurrent read-modify-write cycles on the
     * same room silently lose one of the updates. Until conditional writes land with
     * {@code RoomService} (LT-27), callers must serialise their own mutations per room.
     *
     * @throws IllegalArgumentException if {@code room} is null or carries no room code
     */
    void save(RoomState room);

    /**
     * Reads a room. Does not extend the TTL — use {@link #touch(String)} for that.
     *
     * @return empty when no room is stored under {@code roomCode}, whether unknown or expired
     */
    Optional<RoomState> findByCode(String roomCode);

    /**
     * Lightweight liveness heartbeat: extends the stored room's idle TTL and nothing else.
     *
     * <p>Intentionally does <strong>not</strong> rewrite the stored document, so the
     * {@code lastActiveAt} inside it goes stale between saves. That is the trade: presence traffic
     * arrives far more often than state changes and must not pay to re-serialise the whole room.
     * Persist {@code lastActiveAt} through {@link #save(RoomState)} when it has to be read back.
     *
     * @return {@code false} when no room is stored under {@code roomCode}
     */
    boolean touch(String roomCode);

    /**
     * @return whether a room is currently stored under {@code roomCode}
     */
    boolean existsByCode(String roomCode);

    /**
     * @return {@code true} when a room was deleted, {@code false} when none was stored
     */
    boolean deleteByCode(String roomCode);
}
