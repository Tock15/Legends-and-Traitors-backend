package com.seproduction.legendsandtraitors.room.controller;

import com.seproduction.legendsandtraitors.common.exception.BaseGameException;
import com.seproduction.legendsandtraitors.common.exception.GameAlreadyStartedException;
import com.seproduction.legendsandtraitors.common.exception.PlayerBannedException;
import com.seproduction.legendsandtraitors.common.exception.RoomFullException;
import com.seproduction.legendsandtraitors.common.exception.RoomNotFoundException;
import com.seproduction.legendsandtraitors.room.dto.JoinRoomMessage;
import com.seproduction.legendsandtraitors.room.dto.LobbyErrorMessage;
import com.seproduction.legendsandtraitors.room.dto.LobbyStateBroadcast;
import com.seproduction.legendsandtraitors.room.model.RoomState;
import com.seproduction.legendsandtraitors.room.service.RoomService;
import com.seproduction.legendsandtraitors.security.JwtPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

@Slf4j
@Controller
@RequiredArgsConstructor
class LobbyWsController {

    private static final String ERRORS_QUEUE = "/queue/errors";
    private static final String INVALID_PAYLOAD = "INVALID_PAYLOAD";
    private static final String MALFORMED_PAYLOAD_MESSAGE = "Message payload is malformed.";

    private final RoomService roomService;
    private final SimpMessagingTemplate simpMessagingTemplate;

    /** Identity comes from the CONNECT-frame principal, never the body; an empty body means no colour. */
    @MessageMapping("/lobby/{roomCode}/join")
    void join(@DestinationVariable String roomCode,
              JwtPrincipal principal,
              @Valid @Payload(required = false) JoinRoomMessage message) {
        String color = message == null ? null : message.color();
        RoomState room = roomService.joinRoom(roomCode, principal.id(), principal.displayName(), color);
        simpMessagingTemplate.convertAndSend("/topic/lobby/" + room.getRoomCode(), LobbyStateBroadcast.from(room));
    }

    @MessageExceptionHandler
    @SendToUser(destinations = ERRORS_QUEUE, broadcast = false)
    LobbyErrorMessage handleGameException(BaseGameException ex) {
        log.warn("Lobby action rejected [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return switch (ex) {
            case RoomNotFoundException notFound ->
                    new LobbyErrorMessage(RoomNotFoundException.ERROR_CODE, "This lobby does not exist or has expired.");
            case GameAlreadyStartedException started ->
                    new LobbyErrorMessage("ROOM_IN_GAME", "Game is already in progress.");
            case RoomFullException full -> new LobbyErrorMessage(RoomFullException.ERROR_CODE,
                    "This lobby is full (maximum " + full.getMaxPlayers() + " players).");
            case PlayerBannedException banned -> new LobbyErrorMessage(PlayerBannedException.ERROR_CODE,
                    "You have been temporarily removed from this lobby. Please try again later.");
            default -> new LobbyErrorMessage(ex.getErrorCode(), ex.getMessage());
        };
    }

    @MessageExceptionHandler
    @SendToUser(destinations = ERRORS_QUEUE, broadcast = false)
    LobbyErrorMessage handleInvalidPayload(MethodArgumentNotValidException ex) {
        log.debug("Rejected lobby payload: {}", ex.getMessage());
        BindingResult bindingResult = ex.getBindingResult();
        FieldError error = bindingResult == null ? null : bindingResult.getFieldError();
        if (error == null) {
            return new LobbyErrorMessage(INVALID_PAYLOAD, MALFORMED_PAYLOAD_MESSAGE);
        }
        String code = "color".equals(error.getField()) ? "INVALID_COLOR" : INVALID_PAYLOAD;
        return new LobbyErrorMessage(code, error.getDefaultMessage());
    }

    @MessageExceptionHandler
    @SendToUser(destinations = ERRORS_QUEUE, broadcast = false)
    LobbyErrorMessage handleUnreadablePayload(MessageConversionException ex) {
        log.debug("Unreadable lobby payload: {}", ex.getMessage());
        return new LobbyErrorMessage(INVALID_PAYLOAD, MALFORMED_PAYLOAD_MESSAGE);
    }

    @MessageExceptionHandler
    @SendToUser(destinations = ERRORS_QUEUE, broadcast = false)
    LobbyErrorMessage handleUnexpected(Exception ex) {
        log.error("Unhandled error in lobby message handler", ex);
        return new LobbyErrorMessage("INTERNAL_ERROR", "Something went wrong. Please try again.");
    }
}
