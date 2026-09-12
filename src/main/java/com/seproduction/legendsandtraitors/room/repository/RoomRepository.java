package com.seproduction.legendsandtraitors.room.repository;

import com.seproduction.legendsandtraitors.room.model.RoomState;

import java.util.Optional;

public interface RoomRepository {

    void save(RoomState room);

    Optional<RoomState> findByCode(String roomCode);

    boolean touch(String roomCode);

    boolean existsByCode(String roomCode);

    boolean deleteByCode(String roomCode);
}
