package com.seproduction.legendsandtraitors.room.controller;

import com.seproduction.legendsandtraitors.common.exception.GameAlreadyStartedException;
import com.seproduction.legendsandtraitors.common.exception.InvalidRequestException;
import com.seproduction.legendsandtraitors.common.exception.PlayerBannedException;
import com.seproduction.legendsandtraitors.common.exception.RoomFullException;
import com.seproduction.legendsandtraitors.common.exception.RoomNotFoundException;
import com.seproduction.legendsandtraitors.room.dto.JoinRoomMessage;
import com.seproduction.legendsandtraitors.room.dto.LobbyErrorMessage;
import com.seproduction.legendsandtraitors.room.dto.LobbyStateBroadcast;
import com.seproduction.legendsandtraitors.room.dto.PlayerSlotDto;
import com.seproduction.legendsandtraitors.room.dto.RoleSettingsDto;
import com.seproduction.legendsandtraitors.room.model.PlayerSlot;
import com.seproduction.legendsandtraitors.room.model.RoleSettings;
import com.seproduction.legendsandtraitors.room.model.RoomState;
import com.seproduction.legendsandtraitors.room.model.RoomStatus;
import com.seproduction.legendsandtraitors.room.service.RoomService;
import com.seproduction.legendsandtraitors.security.JwtPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LobbyWsControllerTest {

    private static final String ROOM_CODE = "WXYZ89";
    private static final JwtPrincipal GUEST = new JwtPrincipal("guest_114205", "Guest114205", true, false);

    @Mock
    private RoomService roomService;

    @Mock
    private SimpMessagingTemplate simpMessagingTemplate;

    private LobbyWsController controller;

    @BeforeEach
    void setUp() {
        controller = new LobbyWsController(roomService, simpMessagingTemplate);
    }

    private static RoomState joinedRoom() {
        PlayerSlot host = PlayerSlot.builder()
                .id("guest_948201").displayName("Guest948201").host(true).ready(true).color("#E53E3E").build();
        PlayerSlot guest = PlayerSlot.builder()
                .id(GUEST.id()).displayName(GUEST.displayName()).color("#3182CE").build();
        return RoomState.builder()
                .roomCode(ROOM_CODE)
                .status(RoomStatus.LOBBY)
                .hostId("guest_948201")
                .maxPlayers(8)
                .players(new ArrayList<>(List.of(host, guest)))
                .settings(RoleSettings.builder().king(1).loyalist(2).rebel(3).spy(1).build())
                .build();
    }

    @Test
    @DisplayName("Should join as the principal and broadcast the lobby state to the room's canonical topic")
    void shouldJoinAsPrincipalAndBroadcast() {
        given(roomService.joinRoom("wxyz89", GUEST.id(), GUEST.displayName(), "#3182CE")).willReturn(joinedRoom());

        controller.join("wxyz89", GUEST, new JoinRoomMessage("#3182CE"));

        ArgumentCaptor<LobbyStateBroadcast> captor = ArgumentCaptor.forClass(LobbyStateBroadcast.class);
        verify(simpMessagingTemplate).convertAndSend(eq("/topic/lobby/" + ROOM_CODE), captor.capture());
        assertThat(captor.getValue()).isEqualTo(new LobbyStateBroadcast(
                "LOBBY_STATE", ROOM_CODE, "LOBBY", "guest_948201", 2, 8,
                List.of(new PlayerSlotDto("guest_948201", "Guest948201", true, true, false, "#E53E3E"),
                        new PlayerSlotDto(GUEST.id(), GUEST.displayName(), false, false, false, "#3182CE")),
                new RoleSettingsDto(1, 2, 3, 1)));
    }

    @Test
    @DisplayName("Should treat a missing payload as a join without a colour")
    void shouldTreatMissingPayloadAsNoColor() {
        given(roomService.joinRoom(ROOM_CODE, GUEST.id(), GUEST.displayName(), null)).willReturn(joinedRoom());

        controller.join(ROOM_CODE, GUEST, null);

        verify(roomService).joinRoom(ROOM_CODE, GUEST.id(), GUEST.displayName(), null);
    }

    @Test
    @DisplayName("Should map an unknown room to ROOM_NOT_FOUND")
    void shouldMapRoomNotFound() {
        assertThat(controller.handleGameException(RoomNotFoundException.forRoomCode(ROOM_CODE)))
                .isEqualTo(new LobbyErrorMessage("ROOM_NOT_FOUND", "This lobby does not exist or has expired."));
    }

    @Test
    @DisplayName("Should map a started game to ROOM_IN_GAME")
    void shouldMapGameAlreadyStarted() {
        assertThat(controller.handleGameException(GameAlreadyStartedException.forRoom(ROOM_CODE)))
                .isEqualTo(new LobbyErrorMessage("ROOM_IN_GAME", "Game is already in progress."));
    }

    @Test
    @DisplayName("Should map a full room to ROOM_FULL quoting that room's capacity")
    void shouldMapRoomFull() {
        assertThat(controller.handleGameException(RoomFullException.forRoom(ROOM_CODE, 6)))
                .isEqualTo(new LobbyErrorMessage("ROOM_FULL", "This lobby is full (maximum 6 players)."));
    }

    @Test
    @DisplayName("Should map a ban to PLAYER_BANNED without naming the player or room")
    void shouldMapPlayerBanned() {
        assertThat(controller.handleGameException(PlayerBannedException.forPlayer(GUEST.id(), ROOM_CODE)))
                .isEqualTo(new LobbyErrorMessage("PLAYER_BANNED",
                        "You have been temporarily removed from this lobby. Please try again later."));
    }

    @Test
    @DisplayName("Should pass any other game exception through with its own code and message")
    void shouldPassThroughUnmappedGameException() {
        assertThat(controller.handleGameException(new InvalidRequestException("maxPlayers must be between 4 and 10")))
                .isEqualTo(new LobbyErrorMessage("INVALID_REQUEST", "maxPlayers must be between 4 and 10"));
    }

    @Test
    @DisplayName("Should map a payload that fails validation to INVALID_COLOR")
    void shouldMapInvalidPayload() throws NoSuchMethodException {
        MethodParameter payloadParameter = new MethodParameter(LobbyWsController.class.getDeclaredMethod(
                "join", String.class, JwtPrincipal.class, JoinRoomMessage.class), 2);
        MethodArgumentNotValidException invalid = new MethodArgumentNotValidException(
                MessageBuilder.withPayload(new byte[0]).build(), payloadParameter);

        assertThat(controller.handleInvalidPayload(invalid))
                .isEqualTo(new LobbyErrorMessage("INVALID_COLOR", "Color must be a hex value like #3182CE."));
    }

    @Test
    @DisplayName("Should hide an unexpected failure behind INTERNAL_ERROR")
    void shouldHideUnexpectedFailure() {
        LobbyErrorMessage error = controller.handleUnexpected(
                new IllegalStateException("Unable to join room WXYZ89 after 3 conflicting writes"));

        assertThat(error).isEqualTo(new LobbyErrorMessage("INTERNAL_ERROR", "Something went wrong. Please try again."));
    }
}
