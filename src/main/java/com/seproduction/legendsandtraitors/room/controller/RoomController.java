package com.seproduction.legendsandtraitors.room.controller;

import com.seproduction.legendsandtraitors.room.model.CreateRoomRequest;
import com.seproduction.legendsandtraitors.room.model.CreateRoomResponse;
import com.seproduction.legendsandtraitors.room.service.RoomService;
import com.seproduction.legendsandtraitors.security.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
class RoomController {

    private final RoomService roomService;

    /**
     * The host identity comes from the token, never the body.
     *
     * <p>A null principal is what a missing {@code Authorization} header looks like here: the JWT
     * filter rejects a bad token itself but lets an unauthenticated request through.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CreateRoomResponse createRoom(@RequestBody(required = false) CreateRoomRequest request,
                                  JwtPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing bearer token");
        }
        return roomService.createRoom(principal.id(), principal.displayName(), request);
    }
}
