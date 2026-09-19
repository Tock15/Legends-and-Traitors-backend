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
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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

        AtomicReference<StompHeaders> connectedHeadersRef = new AtomicReference<>();
        CompletableFuture<StompSession> sessionFuture = stompClient.connectAsync(
                "ws://localhost:" + port + "/ws/lobby",
                new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                        connectedHeadersRef.set(connectedHeaders);
                    }
                }
        );

        StompSession session = sessionFuture.get(5, TimeUnit.SECONDS);

        assertThat(session.isConnected()).isTrue();
        StompHeaders connectedHeaders = connectedHeadersRef.get();
        assertThat(connectedHeaders).isNotNull();
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
}
