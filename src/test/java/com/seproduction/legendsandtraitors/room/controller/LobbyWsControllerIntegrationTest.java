package com.seproduction.legendsandtraitors.room.controller;

import com.seproduction.legendsandtraitors.room.model.CreateRoomRequest;
import com.seproduction.legendsandtraitors.room.model.PlayerSlot;
import com.seproduction.legendsandtraitors.room.model.RoomState;
import com.seproduction.legendsandtraitors.room.model.RoomStatus;
import com.seproduction.legendsandtraitors.room.repository.RoomRepository;
import com.seproduction.legendsandtraitors.room.service.RoomService;
import com.seproduction.legendsandtraitors.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.converter.SimpleMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LobbyWsControllerIntegrationTest {

    private static final long TIMEOUT_SECONDS = 5;
    private static final String HOST_ID = "guest_948201";

    @LocalServerPort
    private int port;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RoomService roomService;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private WebSocketStompClient stompClient;
    private ThreadPoolTaskScheduler taskScheduler;
    private final List<StompSession> sessions = new ArrayList<>();
    private final List<String> redisKeys = new ArrayList<>();

    @BeforeEach
    void setUp() {
        taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.initialize();

        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
        stompClient.setTaskScheduler(taskScheduler);
        stompClient.setDefaultHeartbeat(new long[]{10_000L, 10_000L});
    }

    @AfterEach
    void tearDown() {
        sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
        stompClient.stop();
        taskScheduler.destroy();
        stringRedisTemplate.delete(redisKeys);
    }

    private static String displayName(String playerId) {
        return "Guest" + playerId.substring("guest_".length());
    }

    private String createRoom(Integer maxPlayers) {
        String roomCode = roomService.createRoom(HOST_ID, displayName(HOST_ID), new CreateRoomRequest(maxPlayers))
                .roomCode();
        redisKeys.add("room:" + roomCode);
        return roomCode;
    }

    private void ban(String roomCode, String playerId) {
        String banKey = "room:" + roomCode + ":banned:" + playerId;
        redisKeys.add(banKey);
        stringRedisTemplate.opsForValue().set(banKey, "1", Duration.ofMinutes(5));
    }

    private List<String> storedRosterIds(String roomCode) {
        return roomRepository.findByCode(roomCode).orElseThrow().getPlayers().stream()
                .map(PlayerSlot::getId)
                .toList();
    }

    /** Subscribes to the lobby topic and the caller's error queue before anything is sent. */
    private LobbyClient connect(String playerId, String topicRoomCode) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization",
                "Bearer " + jwtTokenProvider.issueToken(playerId, displayName(playerId), true, false));

        StompSession session = stompClient.connectAsync(
                "ws://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {}
        ).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        sessions.add(session);

        LobbyClient client = new LobbyClient(session);
        session.subscribe("/topic/lobby/" + topicRoomCode, queueing(client.states));
        session.subscribe("/user/queue/errors", queueing(client.errors));
        return client;
    }

    private static StompFrameHandler queueing(BlockingQueue<Map<String, Object>> queue) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add((Map<String, Object>) payload);
            }
        };
    }

    private record LobbyClient(StompSession session,
                               BlockingQueue<Map<String, Object>> states,
                               BlockingQueue<Map<String, Object>> errors) {

        LobbyClient(StompSession session) {
            this(session, new LinkedBlockingQueue<>(), new LinkedBlockingQueue<>());
        }

        void join(String roomCode, String color) {
            session.send("/app/lobby/" + roomCode + "/join", color == null ? Map.of() : Map.of("color", color));
        }

        void ready(String roomCode, Boolean isReady) {
            session.send("/app/lobby/" + roomCode + "/ready",
                    isReady == null ? Map.of() : Map.of("isReady", isReady));
        }

        Map<String, Object> nextBroadcast() throws InterruptedException {
            Map<String, Object> broadcast = states.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(broadcast).as("broadcast on /topic/lobby/{roomCode}").isNotNull();
            return broadcast;
        }

        Map<String, Object> nextState() throws InterruptedException {
            Map<String, Object> state = states.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(state).as("LOBBY_STATE broadcast").isNotNull();
            return state;
        }

        Map<String, Object> nextError() throws InterruptedException {
            Map<String, Object> error = errors.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(error).as("error on /user/queue/errors").isNotNull();
            return error;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> players(Map<String, Object> state) {
        return (List<Map<String, Object>>) state.get("players");
    }

    @Test
    @DisplayName("Should broadcast a growing roster to every subscriber as the host and three guests join in turn")
    void shouldBroadcastGrowingRosterAsFourPlayersJoin() throws Exception {
        String roomCode = createRoom(null);
        List<String> ids = List.of(HOST_ID, "guest_114205", "guest_220011", "guest_330022");
        List<String> colors = List.of("#E53E3E", "#3182CE", "#38A169", "#D69E2E");
        List<LobbyClient> joined = new ArrayList<>();
        Map<String, Object> lastState = null;

        for (int i = 0; i < ids.size(); i++) {
            LobbyClient client = connect(ids.get(i), roomCode);
            client.join(roomCode, colors.get(i));
            joined.add(client);

            for (LobbyClient subscriber : joined) {
                lastState = subscriber.nextState();
                assertThat(players(lastState)).hasSize(i + 1);
                assertThat(lastState).containsEntry("totalPlayers", i + 1);
            }
        }

        assertThat(lastState)
                .containsEntry("event", "LOBBY_STATE")
                .containsEntry("roomCode", roomCode)
                .containsEntry("status", "LOBBY")
                .containsEntry("hostId", HOST_ID)
                .containsEntry("maxPlayers", 8)
                .containsEntry("canStartGame", false)
                .containsEntry("settings", Map.of("king", 1, "loyalist", 1, "rebel", 1, "spy", 1));
        assertThat(players(lastState)).extracting(p -> p.get("id")).containsExactlyElementsOf(ids);
        assertThat(players(lastState)).extracting(p -> p.get("color")).containsExactlyElementsOf(colors);
        assertThat(players(lastState)).extracting(p -> p.get("displayName"))
                .containsExactly("Guest948201", "Guest114205", "Guest220011", "Guest330022");
        assertThat(players(lastState)).extracting(p -> p.get("isHost")).containsExactly(true, false, false, false);
        assertThat(players(lastState)).extracting(p -> p.get("isReady")).containsExactly(true, false, false, false);
        assertThat(players(lastState)).extracting(p -> p.get("isAfk")).containsOnly(false);
        assertThat(players(lastState).getFirst())
                .containsOnlyKeys("id", "displayName", "isHost", "isReady", "isAfk", "color");
    }

    @Test
    @DisplayName("Should send ROOM_FULL to a player joining a full room and leave the roster unchanged")
    void shouldRejectJoinToFullRoom() throws Exception {
        String roomCode = createRoom(4);
        for (String seated : List.of("guest_100001", "guest_100002", "guest_100003")) {
            roomService.joinRoom(roomCode, seated, displayName(seated), null);
        }
        LobbyClient latecomer = connect("guest_100004", roomCode);

        latecomer.join(roomCode, "#3182CE");

        assertThat(latecomer.nextError())
                .containsEntry("code", "ROOM_FULL")
                .containsEntry("message", "This lobby is full (maximum 4 players).");
        assertThat(latecomer.states()).isEmpty();
        assertThat(storedRosterIds(roomCode)).hasSize(4);
    }

    @Test
    @DisplayName("Should let a connected host re-join a full room from a second live session without adding a slot")
    void shouldLetConnectedHostRejoinFullRoom() throws Exception {
        String roomCode = createRoom(4);
        List<String> guests = List.of("guest_100001", "guest_100002", "guest_100003");
        for (String seated : guests) {
            roomService.joinRoom(roomCode, seated, displayName(seated), null);
        }
        LobbyClient firstTab = connect(HOST_ID, roomCode);
        firstTab.join(roomCode, "#E53E3E");
        assertThat(players(firstTab.nextState())).hasSize(4);

        LobbyClient secondTab = connect(HOST_ID, roomCode);
        secondTab.join(roomCode, null);

        for (Map<String, Object> state : List.of(firstTab.nextState(), secondTab.nextState())) {
            assertThat(players(state)).extracting(p -> p.get("id"))
                    .containsExactly(HOST_ID, guests.get(0), guests.get(1), guests.get(2));
            assertThat(players(state).getFirst())
                    .containsEntry("color", "#E53E3E")
                    .containsEntry("isAfk", false)
                    .containsEntry("isHost", true);
        }
        assertThat(firstTab.errors()).isEmpty();
        assertThat(secondTab.errors()).isEmpty();
        assertThat(storedRosterIds(roomCode)).doesNotHaveDuplicates().hasSize(4);
    }

    @Test
    @DisplayName("Should send INVALID_PAYLOAD for a join body that is not valid JSON and leave the roster unchanged")
    void shouldRejectMalformedJson() throws Exception {
        String roomCode = createRoom(null);
        stompClient.setMessageConverter(new CompositeMessageConverter(
                List.of(new SimpleMessageConverter(), new JacksonJsonMessageConverter())));
        LobbyClient client = connect("guest_114205", roomCode);
        StompHeaders headers = new StompHeaders();
        headers.setDestination("/app/lobby/" + roomCode + "/join");
        headers.setContentType(MimeTypeUtils.APPLICATION_JSON);

        client.session().send(headers, "{not json".getBytes(StandardCharsets.UTF_8));

        assertThat(client.nextError())
                .containsEntry("code", "INVALID_PAYLOAD")
                .containsEntry("message", "Message payload is malformed.");
        assertThat(client.states()).isEmpty();
        assertThat(storedRosterIds(roomCode)).containsExactly(HOST_ID);
    }

    @Test
    @DisplayName("Should send PLAYER_BANNED to a player whose kick ban is still active")
    void shouldRejectBannedPlayer() throws Exception {
        String roomCode = createRoom(null);
        ban(roomCode, "guest_114205");
        LobbyClient banned = connect("guest_114205", roomCode);

        banned.join(roomCode, "#3182CE");

        assertThat(banned.nextError())
                .containsEntry("code", "PLAYER_BANNED")
                .containsEntry("message",
                        "You have been temporarily removed from this lobby. Please try again later.");
        assertThat(storedRosterIds(roomCode)).containsExactly(HOST_ID);
    }

    @Test
    @DisplayName("Should send ROOM_IN_GAME to a player joining a room whose game has started")
    void shouldRejectJoinToStartedGame() throws Exception {
        String roomCode = createRoom(null);
        RoomState room = roomRepository.findByCode(roomCode).orElseThrow();
        room.setStatus(RoomStatus.GAME_ACTIVE);
        roomRepository.save(room);
        LobbyClient client = connect("guest_114205", roomCode);

        client.join(roomCode, null);

        assertThat(client.nextError())
                .containsEntry("code", "ROOM_IN_GAME")
                .containsEntry("message", "Game is already in progress.");
    }

    @Test
    @DisplayName("Should send ROOM_NOT_FOUND for a code no room is stored under")
    void shouldRejectUnknownRoom() throws Exception {
        // 'O' and '0' are outside the generator's alphabet, so no real room can hold this code.
        String unknownCode = "NOPE00";
        LobbyClient client = connect("guest_114205", unknownCode);

        client.join(unknownCode, null);

        assertThat(client.nextError())
                .containsEntry("code", "ROOM_NOT_FOUND")
                .containsEntry("message", "This lobby does not exist or has expired.");
    }

    @Test
    @DisplayName("Should send INVALID_COLOR for a colour that is not #RRGGBB and leave the roster unchanged")
    void shouldRejectInvalidColor() throws Exception {
        String roomCode = createRoom(null);
        LobbyClient client = connect("guest_114205", roomCode);

        client.join(roomCode, "red");

        assertThat(client.nextError())
                .containsEntry("code", "INVALID_COLOR")
                .containsEntry("message", "Color must be a hex value like #3182CE.");
        assertThat(storedRosterIds(roomCode)).containsExactly(HOST_ID);
    }

    @Test
    @DisplayName("Should accept a lower-case room code and broadcast on the canonical upper-case topic")
    void shouldJoinWithLowerCaseCode() throws Exception {
        String roomCode = createRoom(null);
        LobbyClient client = connect("guest_114205", roomCode);

        client.join(roomCode.toLowerCase(), null);

        Map<String, Object> state = client.nextState();
        assertThat(state).containsEntry("roomCode", roomCode);
        assertThat(players(state)).extracting(p -> p.get("id")).containsExactly(HOST_ID, "guest_114205");
    }

    @Test
    @DisplayName("Should broadcast PLAYER_READY_CHANGED and update canStartGame when guest toggles ready")
    void shouldBroadcastPlayerReadyChanged() throws Exception {
        String roomCode = createRoom(null);
        LobbyClient host = connect(HOST_ID, roomCode);
        host.join(roomCode, "#E53E3E");
        host.nextState();

        LobbyClient guest = connect("guest_114205", roomCode);
        guest.join(roomCode, "#3182CE");
        host.nextState();
        guest.nextState();

        guest.ready(roomCode, true);

        for (LobbyClient client : List.of(host, guest)) {
            Map<String, Object> broadcast = client.nextBroadcast();
            assertThat(broadcast)
                    .containsEntry("event", "PLAYER_READY_CHANGED")
                    .containsEntry("playerId", "guest_114205")
                    .containsEntry("isReady", true)
                    .containsEntry("canStartGame", true);
        }

        guest.ready(roomCode, false);

        for (LobbyClient client : List.of(host, guest)) {
            Map<String, Object> broadcast = client.nextBroadcast();
            assertThat(broadcast)
                    .containsEntry("event", "PLAYER_READY_CHANGED")
                    .containsEntry("playerId", "guest_114205")
                    .containsEntry("isReady", false)
                    .containsEntry("canStartGame", false);
        }
    }

    @Test
    @DisplayName("Should send INVALID_ACTION to a player trying to toggle ready without joining the room")
    void shouldRejectReadyFromUnseatedPlayer() throws Exception {
        String roomCode = createRoom(null);
        LobbyClient outsider = connect("guest_999999", roomCode);

        outsider.ready(roomCode, true);

        assertThat(outsider.nextError())
                .containsEntry("code", "INVALID_ACTION")
                .containsEntry("message", "Player is not in this room");
        assertThat(outsider.states()).isEmpty();
    }

    @Test
    @DisplayName("Should send INVALID_PAYLOAD when ready payload is malformed")
    void shouldRejectMalformedReadyPayload() throws Exception {
        String roomCode = createRoom(null);
        LobbyClient guest = connect("guest_114205", roomCode);
        guest.join(roomCode, "#3182CE");
        guest.nextState();

        stompClient.setMessageConverter(new CompositeMessageConverter(
                List.of(new SimpleMessageConverter(), new JacksonJsonMessageConverter())));
        LobbyClient malformedClient = connect("guest_222222", roomCode);
        malformedClient.join(roomCode, null);
        guest.nextState();
        malformedClient.nextState();

        StompHeaders headers = new StompHeaders();
        headers.setDestination("/app/lobby/" + roomCode + "/ready");
        headers.setContentType(MimeTypeUtils.APPLICATION_JSON);
        malformedClient.session().send(headers, "{not valid json".getBytes(StandardCharsets.UTF_8));

        assertThat(malformedClient.nextError())
                .containsEntry("code", "INVALID_PAYLOAD")
                .containsEntry("message", "Message payload is malformed.");
    }

    @Test
    @DisplayName("Should handle concurrent ready toggles without lost updates in Redis")
    void shouldHandleConcurrentReadyToggles() throws Exception {
        String roomCode = createRoom(null);
        List<String> guests = List.of("guest_100001", "guest_100002", "guest_100003");
        List<LobbyClient> clients = new ArrayList<>();

        for (String guestId : guests) {
            LobbyClient client = connect(guestId, roomCode);
            client.join(roomCode, null);
            clients.add(client);
        }

        // Drain initial join states
        for (LobbyClient client : clients) {
            while (client.states().poll(100, TimeUnit.MILLISECONDS) != null) {
                // drain
            }
        }

        // Concurrently send ready from all 3 guests
        clients.parallelStream().forEach(client -> client.ready(roomCode, true));

        // Poll until all 3 have updated or timeout
        long deadline = System.currentTimeMillis() + 5000;
        RoomState room = null;
        while (System.currentTimeMillis() < deadline) {
            room = roomRepository.findByCode(roomCode).orElseThrow();
            boolean allGuestsReady = room.getPlayers().stream()
                    .filter(p -> !p.isHost())
                    .allMatch(PlayerSlot::isReady);
            if (allGuestsReady) {
                break;
            }
            Thread.sleep(50);
        }

        assertThat(room).isNotNull();
        assertThat(room.getPlayers().stream().filter(p -> !p.isHost()))
                .allMatch(PlayerSlot::isReady);
        assertThat(roomService.canStartGame(room)).isTrue();
    }

    @Test
    @DisplayName("Should broadcast LOBBY_STATE with canStartGame=false when new unready guest joins previously ready lobby")
    void shouldBroadcastLobbyStateWithCanStartGameWhenNewGuestJoins() throws Exception {
        String roomCode = createRoom(null);
        LobbyClient host = connect(HOST_ID, roomCode);
        host.join(roomCode, "#E53E3E");
        host.nextState();

        LobbyClient guest1 = connect("guest_114205", roomCode);
        guest1.join(roomCode, "#3182CE");
        Map<String, Object> stateAfterGuest1Join = host.nextState();
        guest1.nextState();
        assertThat(stateAfterGuest1Join).containsEntry("canStartGame", false);

        // Guest 1 readies up -> min-players is 2 in test profile, so canStartGame becomes true
        guest1.ready(roomCode, true);
        Map<String, Object> readyBroadcastHost = host.nextBroadcast();
        guest1.nextBroadcast();
        assertThat(readyBroadcastHost)
                .containsEntry("event", "PLAYER_READY_CHANGED")
                .containsEntry("canStartGame", true);

        // Now Guest 2 joins -> not ready -> LOBBY_STATE must broadcast canStartGame = false
        LobbyClient guest2 = connect("guest_220011", roomCode);
        guest2.join(roomCode, "#38A169");

        Map<String, Object> stateAfterGuest2JoinHost = host.nextState();
        guest1.nextState();
        guest2.nextState();

        assertThat(stateAfterGuest2JoinHost)
                .containsEntry("event", "LOBBY_STATE")
                .containsEntry("canStartGame", false);
    }
}
