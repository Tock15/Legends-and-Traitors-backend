package com.seproduction.legendsandtraitors.room.service;

import com.seproduction.legendsandtraitors.common.exception.GameAlreadyStartedException;
import com.seproduction.legendsandtraitors.common.exception.InvalidRequestException;
import com.seproduction.legendsandtraitors.common.exception.PlayerBannedException;
import com.seproduction.legendsandtraitors.common.exception.RoomFullException;
import com.seproduction.legendsandtraitors.common.exception.RoomNotFoundException;
import com.seproduction.legendsandtraitors.config.GameRoomProperties;
import com.seproduction.legendsandtraitors.room.model.CreateRoomRequest;
import com.seproduction.legendsandtraitors.room.model.CreateRoomResponse;
import com.seproduction.legendsandtraitors.room.model.PlayerSlot;
import com.seproduction.legendsandtraitors.room.model.RoleSettings;
import com.seproduction.legendsandtraitors.room.model.RoomState;
import com.seproduction.legendsandtraitors.room.model.RoomStatus;
import com.seproduction.legendsandtraitors.room.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
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

    @Nested
    @DisplayName("joinRoom")
    class JoinRoom {

        private static final String GUEST_ID = "guest_114205";
        private static final String GUEST_NAME = "Guest114205";
        private static final String GUEST_COLOR = "#3182CE";
        private static final String HOST_COLOR = "#E53E3E";
        private static final Instant EARLIER = NOW.minusSeconds(600);
        private static final long STORED_VERSION = 7;

        private PlayerSlot slot(String id, boolean host) {
            return PlayerSlot.builder()
                    .id(id)
                    .displayName("Name-" + id)
                    .host(host)
                    .ready(host)
                    .afk(false)
                    .joinedAt(EARLIER)
                    .lastActiveAt(EARLIER)
                    .build();
        }

        private RoomState room(RoomStatus status, int maxPlayers, PlayerSlot... seated) {
            return RoomState.builder()
                    .roomCode(ROOM_CODE)
                    .status(status)
                    .hostId(HOST_ID)
                    .maxPlayers(maxPlayers)
                    .players(new ArrayList<>(List.of(seated)))
                    .settings(RoleSettings.builder().king(1).loyalist(1).rebel(1).spy(1).build())
                    .createdAt(EARLIER)
                    .lastActiveAt(EARLIER)
                    .version(STORED_VERSION)
                    .build();
        }

        private RoomState lobbyWithHost() {
            return room(RoomStatus.LOBBY, 8, slot(HOST_ID, true));
        }

        private void givenStored(RoomState room) {
            given(roomRepository.findByCode(ROOM_CODE)).willReturn(Optional.of(room));
        }

        private void givenWriteSucceeds() {
            given(roomRepository.saveIfVersion(any(), anyLong())).willReturn(true);
        }

        private RoomState writtenRoom() {
            ArgumentCaptor<RoomState> captor = ArgumentCaptor.forClass(RoomState.class);
            verify(roomRepository).saveIfVersion(captor.capture(), anyLong());
            return captor.getValue();
        }

        private PlayerSlot slotOf(RoomState room, String playerId) {
            return room.getPlayers().stream().filter(s -> s.getId().equals(playerId)).findFirst().orElseThrow();
        }

        @Test
        @DisplayName("Should reject an unknown room without writing anything")
        void shouldRejectUnknownRoom() {
            given(roomRepository.findByCode(ROOM_CODE)).willReturn(Optional.empty());

            assertThatThrownBy(() -> roomService.joinRoom(ROOM_CODE, GUEST_ID, GUEST_NAME, GUEST_COLOR))
                    .isInstanceOf(RoomNotFoundException.class);

            verify(roomRepository, never()).saveIfVersion(any(), anyLong());
        }

        @Test
        @DisplayName("Should look the room up by its upper-cased code")
        void shouldUpperCaseRoomCodeBeforeLookup() {
            givenStored(lobbyWithHost());
            givenWriteSucceeds();

            RoomState joined = roomService.joinRoom("wxyz89", GUEST_ID, GUEST_NAME, GUEST_COLOR);

            verify(roomRepository).findByCode(ROOM_CODE);
            verify(roomRepository).isBanned(ROOM_CODE, GUEST_ID);
            assertThat(joined.getRoomCode()).isEqualTo(ROOM_CODE);
        }

        @ParameterizedTest
        @EnumSource(value = RoomStatus.class, names = "LOBBY", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Should reject every status other than LOBBY")
        void shouldRejectNonLobbyStatus(RoomStatus status) {
            givenStored(room(status, 8, slot(HOST_ID, true)));

            assertThatThrownBy(() -> roomService.joinRoom(ROOM_CODE, GUEST_ID, GUEST_NAME, GUEST_COLOR))
                    .isInstanceOf(GameAlreadyStartedException.class);

            verify(roomRepository, never()).saveIfVersion(any(), anyLong());
        }

        @Test
        @DisplayName("Should reject a full room with its capacity, before consulting the ban list")
        void shouldRejectFullRoomBeforeBanCheck() {
            givenStored(room(RoomStatus.LOBBY, 4,
                    slot(HOST_ID, true), slot("guest_1", false), slot("guest_2", false), slot("guest_3", false)));

            assertThatThrownBy(() -> roomService.joinRoom(ROOM_CODE, GUEST_ID, GUEST_NAME, GUEST_COLOR))
                    .isInstanceOfSatisfying(RoomFullException.class,
                            full -> assertThat(full.getMaxPlayers()).isEqualTo(4));

            verify(roomRepository, never()).isBanned(anyString(), anyString());
            verify(roomRepository, never()).saveIfVersion(any(), anyLong());
        }

        @Test
        @DisplayName("Should refuse even an already-seated player once the room is full, per the ticket's check order")
        void shouldRejectSeatedPlayerWhenRoomIsFull() {
            givenStored(room(RoomStatus.LOBBY, 4,
                    slot(HOST_ID, true), slot("guest_1", false), slot("guest_2", false), slot("guest_3", false)));

            assertThatThrownBy(() -> roomService.joinRoom(ROOM_CODE, HOST_ID, HOST_NAME, HOST_COLOR))
                    .isInstanceOf(RoomFullException.class);
        }

        @Test
        @DisplayName("Should reject a player whose kick ban is still active")
        void shouldRejectBannedPlayer() {
            givenStored(lobbyWithHost());
            given(roomRepository.isBanned(ROOM_CODE, GUEST_ID)).willReturn(true);

            assertThatThrownBy(() -> roomService.joinRoom(ROOM_CODE, GUEST_ID, GUEST_NAME, GUEST_COLOR))
                    .isInstanceOf(PlayerBannedException.class);

            verify(roomRepository, never()).saveIfVersion(any(), anyLong());
        }

        @Test
        @DisplayName("Should append a new player as a not-ready, non-host slot stamped with the join time")
        void shouldSeatNewPlayer() {
            givenStored(lobbyWithHost());
            givenWriteSucceeds();

            RoomState joined = roomService.joinRoom(ROOM_CODE, GUEST_ID, GUEST_NAME, GUEST_COLOR);

            assertThat(writtenRoom()).isSameAs(joined);
            assertThat(joined.getPlayers()).extracting(PlayerSlot::getId).containsExactly(HOST_ID, GUEST_ID);
            assertThat(slotOf(joined, GUEST_ID)).satisfies(slot -> {
                assertThat(slot.getDisplayName()).isEqualTo(GUEST_NAME);
                assertThat(slot.isHost()).isFalse();
                assertThat(slot.isReady()).isFalse();
                assertThat(slot.isAfk()).isFalse();
                assertThat(slot.getColor()).isEqualTo(GUEST_COLOR);
                assertThat(slot.getJoinedAt()).isEqualTo(NOW);
                assertThat(slot.getLastActiveAt()).isEqualTo(NOW);
            });
        }

        @Test
        @DisplayName("Should seat a player who sent no colour with a null colour")
        void shouldSeatNewPlayerWithoutColor() {
            givenStored(lobbyWithHost());
            givenWriteSucceeds();

            RoomState joined = roomService.joinRoom(ROOM_CODE, GUEST_ID, GUEST_NAME, null);

            assertThat(slotOf(joined, GUEST_ID).getColor()).isNull();
        }

        @Test
        @DisplayName("Should re-activate a seated player's slot instead of adding a second one")
        void shouldReactivateSeatedPlayer() {
            PlayerSlot hostSlot = slot(HOST_ID, true);
            hostSlot.setAfk(true);
            givenStored(room(RoomStatus.LOBBY, 8, hostSlot));
            givenWriteSucceeds();

            RoomState joined = roomService.joinRoom(ROOM_CODE, HOST_ID, "Renamed", HOST_COLOR);

            assertThat(joined.getPlayers()).singleElement().satisfies(slot -> {
                assertThat(slot.isAfk()).isFalse();
                assertThat(slot.getLastActiveAt()).isEqualTo(NOW);
                assertThat(slot.isHost()).isTrue();
                assertThat(slot.isReady()).isTrue();
                assertThat(slot.getJoinedAt()).isEqualTo(EARLIER);
                assertThat(slot.getDisplayName()).isEqualTo("Name-" + HOST_ID);
            });
        }

        @Test
        @DisplayName("Should keep a seated player's existing colour on rejoin")
        void shouldKeepExistingColorOnRejoin() {
            PlayerSlot hostSlot = slot(HOST_ID, true);
            hostSlot.setColor(HOST_COLOR);
            givenStored(room(RoomStatus.LOBBY, 8, hostSlot));
            givenWriteSucceeds();

            RoomState joined = roomService.joinRoom(ROOM_CODE, HOST_ID, HOST_NAME, GUEST_COLOR);

            assertThat(slotOf(joined, HOST_ID).getColor()).isEqualTo(HOST_COLOR);
        }

        @Test
        @DisplayName("Should fill a seated player's missing colour on rejoin")
        void shouldFillMissingColorOnRejoin() {
            givenStored(lobbyWithHost());
            givenWriteSucceeds();

            RoomState joined = roomService.joinRoom(ROOM_CODE, HOST_ID, HOST_NAME, HOST_COLOR);

            assertThat(slotOf(joined, HOST_ID).getColor()).isEqualTo(HOST_COLOR);
        }

        @Test
        @DisplayName("Should write against the version it read, bumping the stored one and the room's activity")
        void shouldWriteAgainstReadVersion() {
            givenStored(lobbyWithHost());
            givenWriteSucceeds();

            roomService.joinRoom(ROOM_CODE, GUEST_ID, GUEST_NAME, GUEST_COLOR);

            ArgumentCaptor<RoomState> captor = ArgumentCaptor.forClass(RoomState.class);
            verify(roomRepository).saveIfVersion(captor.capture(), eq(STORED_VERSION));
            assertThat(captor.getValue().getVersion()).isEqualTo(STORED_VERSION + 1);
            assertThat(captor.getValue().getLastActiveAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("Should retry a lost write on a fresh read that includes the concurrent change")
        void shouldRetryLostWriteOnFreshRead() {
            RoomState beforeRace = lobbyWithHost();
            RoomState afterRace = room(RoomStatus.LOBBY, 8, slot(HOST_ID, true), slot("guest_racer", false));
            afterRace.setVersion(STORED_VERSION + 1);
            given(roomRepository.findByCode(ROOM_CODE)).willReturn(Optional.of(beforeRace), Optional.of(afterRace));
            given(roomRepository.saveIfVersion(any(), anyLong())).willReturn(false, true);

            RoomState joined = roomService.joinRoom(ROOM_CODE, GUEST_ID, GUEST_NAME, GUEST_COLOR);

            ArgumentCaptor<Long> versions = ArgumentCaptor.forClass(Long.class);
            verify(roomRepository, times(2)).saveIfVersion(any(), versions.capture());
            assertThat(versions.getAllValues()).containsExactly(STORED_VERSION, STORED_VERSION + 1);
            assertThat(joined.getPlayers()).extracting(PlayerSlot::getId)
                    .containsExactly(HOST_ID, "guest_racer", GUEST_ID);
        }

        @Test
        @DisplayName("Should re-run the checks on retry, refusing a room that filled up during the race")
        void shouldRevalidateOnRetry() {
            RoomState beforeRace = room(RoomStatus.LOBBY, 4,
                    slot(HOST_ID, true), slot("guest_1", false), slot("guest_2", false));
            RoomState afterRace = room(RoomStatus.LOBBY, 4,
                    slot(HOST_ID, true), slot("guest_1", false), slot("guest_2", false), slot("guest_racer", false));
            given(roomRepository.findByCode(ROOM_CODE)).willReturn(Optional.of(beforeRace), Optional.of(afterRace));
            given(roomRepository.saveIfVersion(any(), anyLong())).willReturn(false);

            assertThatThrownBy(() -> roomService.joinRoom(ROOM_CODE, GUEST_ID, GUEST_NAME, GUEST_COLOR))
                    .isInstanceOf(RoomFullException.class);

            verify(roomRepository, times(1)).saveIfVersion(any(), anyLong());
        }

        @Test
        @DisplayName("Should give up after three lost writes")
        void shouldGiveUpAfterThreeLostWrites() {
            given(roomRepository.findByCode(ROOM_CODE)).willAnswer(invocation -> Optional.of(lobbyWithHost()));
            given(roomRepository.saveIfVersion(any(), anyLong())).willReturn(false);

            assertThatThrownBy(() -> roomService.joinRoom(ROOM_CODE, GUEST_ID, GUEST_NAME, GUEST_COLOR))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Unable to join room WXYZ89 after 3 conflicting writes");

            verify(roomRepository, times(3)).findByCode(ROOM_CODE);
            verify(roomRepository, times(3)).saveIfVersion(any(), anyLong());
        }

        @Test
        @DisplayName("Should reject blank join arguments before touching Redis")
        void shouldRejectBlankJoinArguments() {
            assertThatThrownBy(() -> roomService.joinRoom(" ", GUEST_ID, GUEST_NAME, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> roomService.joinRoom(ROOM_CODE, " ", GUEST_NAME, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> roomService.joinRoom(ROOM_CODE, GUEST_ID, " ", null))
                    .isInstanceOf(IllegalArgumentException.class);

            verifyNoInteractions(roomRepository);
        }
    }
}
