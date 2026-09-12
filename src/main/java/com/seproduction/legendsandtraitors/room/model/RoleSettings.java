package com.seproduction.legendsandtraitors.room.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleSettings {

    private int king;
    private int loyalist;
    private int rebel;
    private int spy;
}
