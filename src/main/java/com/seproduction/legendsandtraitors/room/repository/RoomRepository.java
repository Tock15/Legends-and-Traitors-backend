package com.seproduction.legendsandtraitors.room.repository;

import com.seproduction.legendsandtraitors.room.model.RoomState;

import java.util.Optional;

/** Every write resets the room's idle TTL, so a lookup cannot tell "expired" from "never existed". */
public interface RoomRepository {

    /** Last write wins: ignores {@code version}, so concurrent read-modify-writes lose an update. */
    void save(RoomState room);

    /** The only safe create: stores the room only while its code is free; {@code false} if taken. */
    boolean saveIfAbsent(RoomState room);

    /** Writes only while the stored version equals {@code expectedVersion}; the caller bumps the version. */
    boolean saveIfVersion(RoomState room, long expectedVersion);

    /** Does not extend the TTL. */
    Optional<RoomState> findByCode(String roomCode);

    /** Extends the TTL without a rewrite, leaving the stored {@code lastActiveAt} stale. */
    boolean touch(String roomCode);

    boolean existsByCode(String roomCode);

    boolean deleteByCode(String roomCode);

    /** Checks the kick-ban key {@code room:{roomCode}:banned:{playerId}}. */
    boolean isBanned(String roomCode, String playerId);
}
