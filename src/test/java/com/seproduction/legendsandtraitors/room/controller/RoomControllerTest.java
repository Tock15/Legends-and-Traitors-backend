package com.seproduction.legendsandtraitors.room.controller;

import com.seproduction.legendsandtraitors.common.InvalidRequestException;
import com.seproduction.legendsandtraitors.config.ApplicationPropertiesConfig;
import com.seproduction.legendsandtraitors.config.JwtFilterConfig;
import com.seproduction.legendsandtraitors.room.model.CreateRoomRequest;
import com.seproduction.legendsandtraitors.room.model.CreateRoomResponse;
import com.seproduction.legendsandtraitors.room.service.RoomService;
import com.seproduction.legendsandtraitors.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@WebMvcTest(RoomController.class)
@ActiveProfiles("test")
@Import({JwtFilterConfig.class, JwtTokenProvider.class, ApplicationPropertiesConfig.class})
class RoomControllerTest {

    private static final String HOST_ID = "guest_948201";
    private static final String HOST_NAME = "Guest948201";
    private static final String ROOM_CODE = "WXYZ89";
    private static final String JOIN_URL = "http://localhost:5173/lobby/" + ROOM_CODE;
    private static final Instant CREATED_AT = Instant.parse("2026-09-08T14:30:00Z");

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private RoomService roomService;

    private String guestBearer() {
        return "Bearer " + jwtTokenProvider.issueToken(HOST_ID, HOST_NAME, true, false);
    }

    private MvcTestResult postRooms(String authorization, String body) {
        var request = mockMvc.post().uri("/api/rooms").contentType(MediaType.APPLICATION_JSON);
        if (authorization != null) {
            request = request.header("Authorization", authorization);
        }
        if (body != null) {
            request = request.content(body);
        }
        return request.exchange();
    }

    private void givenServiceCreatesRoom() {
        given(roomService.createRoom(any(), any(), any()))
                .willReturn(new CreateRoomResponse(ROOM_CODE, JOIN_URL, HOST_ID, CREATED_AT));
    }

    @Test
    @DisplayName("Should answer 201 with the room code, invite URL, host id and creation instant")
    void shouldCreateRoomForAuthenticatedGuest() {
        givenServiceCreatesRoom();

        MvcTestResult result = postRooms(guestBearer(), "{\"maxPlayers\":8}");

        assertThat(result).hasStatus(HttpStatus.CREATED).hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        assertThat(result).bodyJson().extractingPath("$.roomCode").isEqualTo(ROOM_CODE);
        assertThat(result).bodyJson().extractingPath("$.joinUrl").isEqualTo(JOIN_URL);
        assertThat(result).bodyJson().extractingPath("$.hostId").isEqualTo(HOST_ID);
    }

    @Test
    @DisplayName("Should render createdAt as an ISO-8601 instant, not the epoch millis stored in Redis")
    void shouldRenderCreatedAtAsIso8601() {
        givenServiceCreatesRoom();

        assertThat(postRooms(guestBearer(), "{\"maxPlayers\":8}"))
                .bodyJson()
                .extractingPath("$.createdAt").isEqualTo("2026-09-08T14:30:00Z");
    }

    @Test
    @DisplayName("Should take the host identity from the token, never from the request body")
    void shouldTakeHostIdentityFromToken() {
        givenServiceCreatesRoom();

        postRooms(guestBearer(), "{\"maxPlayers\":8,\"hostId\":\"guest_000000\"}");

        verify(roomService).createRoom(eq(HOST_ID), eq(HOST_NAME), any());
    }

    @Test
    @DisplayName("Should pass the requested capacity through to the service")
    void shouldPassRequestedCapacityToService() {
        givenServiceCreatesRoom();

        postRooms(guestBearer(), "{\"maxPlayers\":10}");

        ArgumentCaptor<CreateRoomRequest> captor = ArgumentCaptor.forClass(CreateRoomRequest.class);
        verify(roomService).createRoom(any(), any(), captor.capture());
        assertThat(captor.getValue().maxPlayers()).isEqualTo(10);
    }

    @Test
    @DisplayName("Should accept a request with no body and leave the capacity for the service to default")
    void shouldAcceptRequestWithoutBody() {
        givenServiceCreatesRoom();

        assertThat(postRooms(guestBearer(), null)).hasStatus(HttpStatus.CREATED);

        verify(roomService).createRoom(eq(HOST_ID), eq(HOST_NAME), isNull());
    }

    @Test
    @DisplayName("Should answer 401 when no Authorization header is sent")
    void shouldRejectMissingAuthorizationHeader() {
        assertThat(postRooms(null, "{}")).hasStatus(HttpStatus.UNAUTHORIZED);

        verifyNoInteractions(roomService);
    }

    @Test
    @DisplayName("Should answer 401 when the Authorization header is not a Bearer token")
    void shouldRejectNonBearerAuthorizationHeader() {
        assertThat(postRooms("Basic Z3Vlc3Q6cHc=", "{}")).hasStatus(HttpStatus.UNAUTHORIZED);

        verifyNoInteractions(roomService);
    }

    @Test
    @DisplayName("Should answer 401 problem+json for a malformed token")
    void shouldRejectMalformedToken() {
        assertThat(postRooms("Bearer not-a-jwt", "{}"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);

        verifyNoInteractions(roomService);
    }

    @Test
    @DisplayName("Should answer 400 problem+json stating the range when the service rejects the capacity")
    void shouldRenderBadRequestForRejectedCapacity() {
        willThrow(new InvalidRequestException("maxPlayers must be between 4 and 10"))
                .given(roomService).createRoom(any(), any(), any());

        assertThat(postRooms(guestBearer(), "{\"maxPlayers\":3}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.detail").isEqualTo("maxPlayers must be between 4 and 10");
    }
}
