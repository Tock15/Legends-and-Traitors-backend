package com.seproduction.legendsandtraitors.room.service;

import com.seproduction.legendsandtraitors.common.InvalidRequestException;
import com.seproduction.legendsandtraitors.config.GameRoomProperties;
import com.seproduction.legendsandtraitors.room.model.CreateRoomRequest;
import com.seproduction.legendsandtraitors.room.model.CreateRoomResponse;
import com.seproduction.legendsandtraitors.room.model.PlayerSlot;
import com.seproduction.legendsandtraitors.room.model.RoomState;
import com.seproduction.legendsandtraitors.room.model.RoomStatus;
import com.seproduction.legendsandtraitors.room.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    private static final String HOST_ID = "guest_948201";
    private static final String HOST_NAME = "Guest948201";
    private static final String ROOM_CODE = "WXYZ89";
    private static final Instant NOW = Instant.parse("2026-09-08T14:30:00Z");

    @Mock
    private RoomCodeGenerator roomCodeGenerator;

    @Mock
    private RoomRepository roomRepository;

    private GameRoomProperties gameRoomProperties;

    private RoomService roomService;

    @BeforeEach
    void setUp() {
        gameRoomProperties = new GameRoomProperties();
        roomService = new RoomService(roomCodeGenerator, roomRepository, gameRoomProperties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void givenFirstClaimSucceeds() {
        given(roomCodeGenerator.generateUniqueRoomCode()).willReturn(ROOM_CODE);
        given(roomRepository.saveIfAbsent(any())).willReturn(true);
    }

    private CreateRoomResponse createRoom(Integer maxPlayers) {
        return roomService.createRoom(HOST_ID, HOST_NAME, new CreateRoomRequest(maxPlayers));
    }

    private RoomState claimedRoom() {
        ArgumentCaptor<RoomState> captor = ArgumentCaptor.forClass(RoomState.class);
        verify(roomRepository).saveIfAbsent(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("Should claim a LOBBY room carrying the generated code and the requested capacity")
    void shouldClaimLobbyRoom() {
        givenFirstClaimSucceeds();

        createRoom(6);

        RoomState room = claimedRoom();
        assertThat(room.getRoomCode()).isEqualTo(ROOM_CODE);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.LOBBY);
        assertThat(room.getHostId()).isEqualTo(HOST_ID);
        assertThat(room.getMaxPlayers()).isEqualTo(6);
        assertThat(room.getCreatedAt()).isEqualTo(NOW);
        assertThat(room.getLastActiveAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Should seat the host as a ready host slot with no colour chosen yet")
    void shouldSeatHostAsReadyHostSlot() {
        givenFirstClaimSucceeds();

        createRoom(8);

        assertThat(claimedRoom().getPlayers()).singleElement().satisfies(slot -> {
            assertThat(slot.getId()).isEqualTo(HOST_ID);
            assertThat(slot.getDisplayName()).isEqualTo(HOST_NAME);
            assertThat(slot.isHost()).isTrue();
            assertThat(slot.isReady()).isTrue();
            assertThat(slot.isAfk()).isFalse();
            assertThat(slot.getColor()).isNull();
            assertThat(slot.getJoinedAt()).isEqualTo(NOW);
            assertThat(slot.getLastActiveAt()).isEqualTo(NOW);
        });
    }

    @Test
    @DisplayName("Should seed one of each role for the smallest game")
    void shouldSeedOneOfEachRole() {
        givenFirstClaimSucceeds();

        createRoom(8);

        assertThat(claimedRoom().getSettings()).satisfies(settings -> {
            assertThat(settings.getKing()).isEqualTo(1);
            assertThat(settings.getLoyalist()).isEqualTo(1);
            assertThat(settings.getRebel()).isEqualTo(1);
            assertThat(settings.getSpy()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("Should keep the roster mutable so the lobby join handler can append slots")
    void shouldSeedMutableRoster() {
        givenFirstClaimSucceeds();

        createRoom(8);

        RoomState room = claimedRoom();
        room.getPlayers().add(PlayerSlot.builder().id("guest_111111").build());
        assertThat(room.getPlayers()).hasSize(2);
    }

    @Test
    @DisplayName("Should truncate the creation instant to the millisecond Redis can store")
    void shouldTruncateCreationInstantToMillis() {
        roomService = new RoomService(roomCodeGenerator, roomRepository, gameRoomProperties,
                Clock.fixed(Instant.parse("2026-09-08T14:30:00.401563200Z"), ZoneOffset.UTC));
        givenFirstClaimSucceeds();

        CreateRoomResponse response = createRoom(8);

        Instant expected = Instant.parse("2026-09-08T14:30:00.401Z");
        assertThat(response.createdAt()).isEqualTo(expected);
        assertThat(claimedRoom().getCreatedAt()).isEqualTo(expected);
    }

    @Test
    @DisplayName("Should return the room code, invite URL, host id and creation instant")
    void shouldReturnCreatedRoomDetails() {
        givenFirstClaimSucceeds();

        CreateRoomResponse response = createRoom(8);

        assertThat(response.roomCode()).isEqualTo(ROOM_CODE);
        assertThat(response.joinUrl()).isEqualTo("http://localhost:5173/lobby/" + ROOM_CODE);
        assertThat(response.hostId()).isEqualTo(HOST_ID);
        assertThat(response.createdAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Should build the invite URL from game.room.join-base-url without doubling the slash")
    void shouldTrimTrailingSlashFromJoinBaseUrl() {
        gameRoomProperties.setJoinBaseUrl("https://legends.example/");
        givenFirstClaimSucceeds();

        assertThat(createRoom(8).joinUrl()).isEqualTo("https://legends.example/lobby/" + ROOM_CODE);
    }

    @Test
    @DisplayName("Should fall back to game.room.max-players when maxPlayers is omitted")
    void shouldDefaultCapacityWhenMaxPlayersOmitted() {
        givenFirstClaimSucceeds();

        createRoom(null);

        assertThat(claimedRoom().getMaxPlayers()).isEqualTo(gameRoomProperties.getMaxPlayers());
    }

    @Test
    @DisplayName("Should fall back to game.room.max-players when no body was sent at all")
    void shouldDefaultCapacityWhenRequestIsNull() {
        givenFirstClaimSucceeds();

        roomService.createRoom(HOST_ID, HOST_NAME, null);

        assertThat(claimedRoom().getMaxPlayers()).isEqualTo(gameRoomProperties.getMaxPlayers());
    }

    @Test
    @DisplayName("Should accept both configured capacity boundaries")
    void shouldAcceptConfiguredCapacityBoundaries() {
        givenFirstClaimSucceeds();

        createRoom(gameRoomProperties.getMinCapacity());
        createRoom(gameRoomProperties.getMaxCapacity());

        ArgumentCaptor<RoomState> captor = ArgumentCaptor.forClass(RoomState.class);
        verify(roomRepository, times(2)).saveIfAbsent(captor.capture());
        assertThat(captor.getAllValues()).extracting(RoomState::getMaxPlayers).containsExactly(4, 10);
    }

    @Test
    @DisplayName("Should reject a capacity outside the configured range without touching Redis")
    void shouldRejectCapacityOutsideConfiguredRange() {
        for (int requested : new int[] {-1, 0, 3, 11, 100}) {
            assertThatThrownBy(() -> createRoom(requested))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessage("maxPlayers must be between 4 and 10");
        }

        verifyNoInteractions(roomCodeGenerator, roomRepository);
    }

    @Test
    @DisplayName("Should generate a fresh code when another host claims the first one")
    void shouldRetryWithFreshCodeWhenClaimIsLost() {
        given(roomCodeGenerator.generateUniqueRoomCode()).willReturn("AAAA11", "BBBB22");
        given(roomRepository.saveIfAbsent(any())).willReturn(false, true);

        CreateRoomResponse response = createRoom(8);

        assertThat(response.roomCode()).isEqualTo("BBBB22");
        assertThat(response.joinUrl()).endsWith("/lobby/BBBB22");

        ArgumentCaptor<RoomState> captor = ArgumentCaptor.forClass(RoomState.class);
        verify(roomRepository, times(2)).saveIfAbsent(captor.capture());
        assertThat(captor.getAllValues()).extracting(RoomState::getRoomCode).containsExactly("AAAA11", "BBBB22");
    }

    @Test
    @DisplayName("Should give up after three lost claims rather than overwrite a live room")
    void shouldGiveUpAfterThreeLostClaims() {
        given(roomCodeGenerator.generateUniqueRoomCode()).willReturn(ROOM_CODE);
        given(roomRepository.saveIfAbsent(any())).willReturn(false);

        assertThatThrownBy(() -> createRoom(8))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to claim a unique room code");

        verify(roomCodeGenerator, times(3)).generateUniqueRoomCode();
        verify(roomRepository, times(3)).saveIfAbsent(any());
    }

    @Test
    @DisplayName("Should reject a blank host identity before generating anything")
    void shouldRejectBlankHostIdentity() {
        assertThatThrownBy(() -> roomService.createRoom(" ", HOST_NAME, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> roomService.createRoom(HOST_ID, " ", null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(roomCodeGenerator, roomRepository);
    }
}
