package com.seproduction.legendsandtraitors.room.dto;

import com.seproduction.legendsandtraitors.room.model.RoleSettings;

public record RoleSettingsDto(int king, int loyalist, int rebel, int spy) {

    public static RoleSettingsDto from(RoleSettings settings) {
        return new RoleSettingsDto(settings.getKing(), settings.getLoyalist(), settings.getRebel(), settings.getSpy());
    }
}
