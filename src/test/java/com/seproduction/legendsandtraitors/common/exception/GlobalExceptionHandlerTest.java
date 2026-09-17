package com.seproduction.legendsandtraitors.common.exception;

import com.seproduction.legendsandtraitors.security.InvalidJwtException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit and slice integration tests for {@link GlobalExceptionHandler} and RFC-7807 Problem Details serialization.
 *
 * Related Ticket: LT-33 / BE-15
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("Domain Exception Tests (BaseGameException hierarchy)")
    class DomainExceptionTests {

        @Test
        @DisplayName("Throwing RoomNotFoundException returns 404 with RFC-7807 JSON contract")
        void shouldHandleRoomNotFoundException() throws Exception {
            mockMvc.perform(get("/test/room-not-found"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.title").value("Room Not Found"))
                    .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"))
                    .andExpect(jsonPath("$.detail").value("Room with code 'ABCDEF' was not found."))
                    .andExpect(jsonPath("$.instance").value("/test/room-not-found"))
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        @DisplayName("Throwing RoomFullException returns 409 Conflict with ROOM_FULL code")
        void shouldHandleRoomFullException() throws Exception {
            mockMvc.perform(get("/test/room-full"))
                    .andExpect(status().isConflict())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.title").value("Room Full"))
                    .andExpect(jsonPath("$.code").value("ROOM_FULL"))
                    .andExpect(jsonPath("$.detail").value("Lobby WXYZ89 has reached its maximum capacity of 8 players."))
                    .andExpect(jsonPath("$.instance").value("/test/room-full"))
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        @DisplayName("Throwing GameAlreadyStartedException returns 409 Conflict with GAME_ALREADY_STARTED code")
        void shouldHandleGameAlreadyStartedException() throws Exception {
            mockMvc.perform(get("/test/game-already-started"))
                    .andExpect(status().isConflict())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.title").value("Game Already Started"))
                    .andExpect(jsonPath("$.code").value("GAME_ALREADY_STARTED"))
                    .andExpect(jsonPath("$.detail").value("Game in room 'WXYZ89' has already started."))
                    .andExpect(jsonPath("$.instance").value("/test/game-already-started"))
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        @DisplayName("Throwing PlayerBannedException returns 403 Forbidden with PLAYER_BANNED code")
        void shouldHandlePlayerBannedException() throws Exception {
            mockMvc.perform(get("/test/player-banned"))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.title").value("Player Banned"))
                    .andExpect(jsonPath("$.code").value("PLAYER_BANNED"))
                    .andExpect(jsonPath("$.detail").value("Player 'guest_123' is banned from room 'WXYZ89'."))
                    .andExpect(jsonPath("$.instance").value("/test/player-banned"))
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        @DisplayName("Throwing NotHostException returns 403 Forbidden with NOT_HOST code")
        void shouldHandleNotHostException() throws Exception {
            mockMvc.perform(get("/test/not-host"))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.title").value("Forbidden - Not Host"))
                    .andExpect(jsonPath("$.code").value("NOT_HOST"))
                    .andExpect(jsonPath("$.detail").value("Only the room host is permitted to start the game."))
                    .andExpect(jsonPath("$.instance").value("/test/not-host"))
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        @DisplayName("Throwing InvalidLobbyActionException returns 400 Bad Request with INVALID_ACTION code")
        void shouldHandleInvalidLobbyActionException() throws Exception {
            mockMvc.perform(get("/test/invalid-action"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.title").value("Invalid Lobby Action"))
                    .andExpect(jsonPath("$.code").value("INVALID_ACTION"))
                    .andExpect(jsonPath("$.detail").value("Cannot start game: not all players are ready."))
                    .andExpect(jsonPath("$.instance").value("/test/invalid-action"))
                    .andExpect(jsonPath("$.timestamp").exists());
        }
    }

    @Nested
    @DisplayName("Validation Error Tests")
    class ValidationExceptionTests {

        @Test
        @DisplayName("DTO validation failures return 400 with structured invalidParams array")
        void shouldReturnStructuredInvalidParamsWhenValidationFails() throws Exception {
            String invalidPayload = """
                    {
                        "roomCode": "",
                        "maxPlayers": 2
                    }
                    """;

            mockMvc.perform(post("/test/validate-dto")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidPayload))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.title").value("Bad Request"))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.detail").value("Invalid request parameters."))
                    .andExpect(jsonPath("$.instance").value("/test/validate-dto"))
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.invalidParams").isArray())
                    .andExpect(jsonPath("$.invalidParams[?(@.field == 'maxPlayers')].rejectedValue").value(hasItem(2)))
                    .andExpect(jsonPath("$.invalidParams[?(@.field == 'maxPlayers')].message").value(hasItem("Maximum players must be at least 4")))
                    .andExpect(jsonPath("$.invalidParams[?(@.field == 'roomCode')].rejectedValue").value(hasItem("")))
                    .andExpect(jsonPath("$.invalidParams[?(@.field == 'roomCode')].message").value(hasItem("Room code is required")));
        }
    }

    @Nested
    @DisplayName("JWT Security Exception Tests")
    class JwtExceptionTests {

        @Test
        @DisplayName("Should answer a rejected token with 401 problem+json")
        void shouldRenderUnauthorizedProblemDetail() throws Exception {
            mockMvc.perform(get("/test/rejecting"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.title").value("Unauthorized"))
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                    .andExpect(jsonPath("$.detail").value("Invalid or expired token"))
                    .andExpect(jsonPath("$.instance").value("/test/rejecting"))
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        @DisplayName("Should not leak which verification step failed")
        void shouldNotLeakFailedCheck() throws Exception {
            mockMvc.perform(get("/test/rejecting"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Invalid or expired token"))
                    .andExpect(content().string(not(containsString("signature"))));
        }
    }

    @Nested
    @DisplayName("Unhandled Exception Tests")
    class UnhandledExceptionTests {

        @Test
        @DisplayName("Uncaught exceptions return sanitized 500 without leaking sensitive stack traces")
        void shouldSanitizeUnhandledExceptions() throws Exception {
            mockMvc.perform(get("/test/unhandled-error"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.title").value("Internal Server Error"))
                    .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                    .andExpect(jsonPath("$.detail").value("An unexpected internal server error occurred."))
                    .andExpect(jsonPath("$.instance").value("/test/unhandled-error"))
                    .andExpect(jsonPath("$.timestamp").exists())
                    // Ensure internal sensitive details and stack trace strings are not leaked to the client
                    .andExpect(content().string(not(containsString("super_secret_db_password"))))
                    .andExpect(content().string(not(containsString("NullPointerException"))));
        }
    }

    /**
     * Dedicated controller providing endpoints to exercise exception handling during test execution.
     */
    @RestController
    static class TestController {

        @GetMapping("/test/room-not-found")
        public void throwRoomNotFound() {
            throw RoomNotFoundException.forRoomCode("ABCDEF");
        }

        @GetMapping("/test/room-full")
        public void throwRoomFull() {
            throw RoomFullException.forRoom("WXYZ89", 8);
        }

        @GetMapping("/test/game-already-started")
        public void throwGameAlreadyStarted() {
            throw GameAlreadyStartedException.forRoom("WXYZ89");
        }

        @GetMapping("/test/player-banned")
        public void throwPlayerBanned() {
            throw PlayerBannedException.forPlayer("guest_123", "WXYZ89");
        }

        @GetMapping("/test/not-host")
        public void throwNotHost() {
            throw NotHostException.forAction("start the game");
        }

        @GetMapping("/test/invalid-action")
        public void throwInvalidAction() {
            throw new InvalidLobbyActionException("Cannot start game: not all players are ready.");
        }

        @PostMapping("/test/validate-dto")
        public void validateDto(@Valid @RequestBody TestRequestDto dto) {
            // No-op for testing validation
        }

        @GetMapping("/test/rejecting")
        public void throwInvalidJwt() {
            throw new InvalidJwtException("JWT signature does not match");
        }

        @GetMapping("/test/unhandled-error")
        public void throwUnhandledError() {
            throw new RuntimeException("Crash: super_secret_db_password failed connection!");
        }
    }

    record TestRequestDto(
            @NotBlank(message = "Room code is required")
            String roomCode,

            @Min(value = 4, message = "Maximum players must be at least 4")
            int maxPlayers
    ) {}
}
