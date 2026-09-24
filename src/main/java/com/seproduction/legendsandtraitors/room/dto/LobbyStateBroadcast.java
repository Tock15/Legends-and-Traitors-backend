package com.seproduction.legendsandtraitors.room.dto;

import com.seproduction.legendsandtraitors.room.model.RoomState;

import java.util.List;

public record LobbyStateBroadcast(
        String event,
        String roomCode,
        String status,
        String hostId,
        int totalPlayers,
        int maxPlayers,
        List<PlayerSlotDto> players,
        RoleSettingsDto settings) {

    public static final String EVENT = "LOBBY_STATE";

    public static LobbyStateBroadcast from(RoomState room) {
        List<PlayerSlotDto> players = room.getPlayers().stream().map(PlayerSlotDto::from).toList();
        return new LobbyStateBroadcast(EVENT, room.getRoomCode(), room.getStatus().name(), room.getHostId(),
                players.size(), room.getMaxPlayers(), players, RoleSettingsDto.from(room.getSettings()));
    }
}
