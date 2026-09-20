package com.seproduction.legendsandtraitors.config;

import com.seproduction.legendsandtraitors.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketSecurityIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private WebSocketStompClient stompClient;
    private ThreadPoolTaskScheduler taskScheduler;

    @BeforeEach
    void setUp() {
        taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.initialize();

        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new StringMessageConverter());
        stompClient.setTaskScheduler(taskScheduler);
        stompClient.setDefaultHeartbeat(new long[]{10_000L, 10_000L});
    }

    @AfterEach
    void tearDown() {
        if (stompClient != null) {
            stompClient.stop();
        }
        if (taskScheduler != null) {
            taskScheduler.destroy();
        }
    }

    @Test
    @DisplayName("Should successfully connect to /ws/lobby with valid JWT and negotiate 10s heartbeats")
    void shouldConnectWithValidJwtToWsLobby() throws Exception {
        String token = jwtTokenProvider.issueToken("guest_123456", "Guest123456", true, false);
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);

        // DefaultStompSession completes the session future before invoking afterConnected, so the
        // headers must be awaited separately rather than read once the session is in hand.
        CompletableFuture<StompHeaders> connectedHeadersFuture = new CompletableFuture<>();
        CompletableFuture<StompSession> sessionFuture = stompClient.connectAsync(
                "ws://localhost:" + port + "/ws/lobby",
                new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                        connectedHeadersFuture.complete(connectedHeaders);
                    }
                }
        );

        StompSession session = sessionFuture.get(5, TimeUnit.SECONDS);
        StompHeaders connectedHeaders = connectedHeadersFuture.get(5, TimeUnit.SECONDS);

        assertThat(session.isConnected()).isTrue();
        assertThat(connectedHeaders.getHeartbeat()).containsExactly(10_000L, 10_000L);
        assertThat(connectedHeaders.getFirst("user-name")).isEqualTo("guest_123456");

        session.disconnect();
    }

    @Test
    @DisplayName("Should successfully connect to /ws with valid JWT")
    void shouldConnectWithValidJwtToWs() throws Exception {
        String token = jwtTokenProvider.issueToken("guest_654321", "Guest654321", true, false);
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);

        CompletableFuture<StompSession> sessionFuture = stompClient.connectAsync(
                "ws://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {}
        );

        StompSession session = sessionFuture.get(5, TimeUnit.SECONDS);
        assertThat(session.isConnected()).isTrue();
        session.disconnect();
    }

    @Test
    @DisplayName("Should reject STOMP connection when Authorization header is missing")
    void shouldRejectConnectionWithoutAuthHeader() {
        StompHeaders connectHeaders = new StompHeaders();

        CompletableFuture<StompSession> sessionFuture = stompClient.connectAsync(
                "ws://localhost:" + port + "/ws/lobby",
                new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {}
        );

        assertThatThrownBy(() -> sessionFuture.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
    }

    @Test
    @DisplayName("Should reject STOMP connection when JWT is invalid")
    void shouldRejectConnectionWithInvalidJwt() {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer invalid.jwt.token");

        CompletableFuture<StompSession> sessionFuture = stompClient.connectAsync(
                "ws://localhost:" + port + "/ws/lobby",
                new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {}
        );

        assertThatThrownBy(() -> sessionFuture.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
    }

    @Test
    @DisplayName("Raw WebSocket test: CONNECT without token receives ERROR frame and is closed")
    void testRawConnectWithoutToken() throws Exception {
        StandardWebSocketClient rawClient = new StandardWebSocketClient();
        CompletableFuture<String> responseFuture = new CompletableFuture<>();
        CompletableFuture<CloseStatus> closeFuture = new CompletableFuture<>();

        WebSocketSession rawSession = rawClient.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                responseFuture.complete(message.getPayload());
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
                closeFuture.complete(status);
            }
        }, "ws://localhost:" + port + "/ws/lobby").get(5, TimeUnit.SECONDS);

        String connectFrame = "CONNECT\naccept-version:1.2,1.1,1.0\nheart-beat:10000,10000\n\n\0";
        rawSession.sendMessage(new TextMessage(connectFrame));

        String response = responseFuture.get(5, TimeUnit.SECONDS);
        CloseStatus closeStatus = closeFuture.get(5, TimeUnit.SECONDS);

        assertThat(response).contains("ERROR");
        assertThat(response).contains("missing an Authorization header");
        // Tomcat fires this callback mid-close, while isOpen() still reports true, so assert on the
        // close frame the server sent rather than on the session's not-yet-settled state.
        assertThat(closeStatus.getCode()).isEqualTo(CloseStatus.PROTOCOL_ERROR.getCode());
    }
}
