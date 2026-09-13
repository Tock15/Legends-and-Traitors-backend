package com.seproduction.legendsandtraitors.room.service;

import com.seproduction.legendsandtraitors.config.GameRoomProperties;
import com.seproduction.legendsandtraitors.room.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RoomCodeGeneratorTest {

    /** Spelled out, not imported: the test must fail if the constant grows an ambiguous character. */
    private static final String EXPECTED_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int SAMPLE_SIZE = 200;

    @Mock
    private RoomRepository roomRepository;

    private GameRoomProperties gameRoomProperties;

    private RoomCodeGenerator roomCodeGenerator;

    @BeforeEach
    void setUp() {
        gameRoomProperties = new GameRoomProperties();
        roomCodeGenerator = new RoomCodeGenerator(roomRepository, gameRoomProperties);
    }

    private List<String> generateSample() {
        given(roomRepository.existsByCode(anyString())).willReturn(false);
        List<String> codes = new ArrayList<>(SAMPLE_SIZE);
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            codes.add(roomCodeGenerator.generateUniqueRoomCode());
        }
        return codes;
    }

    @Test
    @DisplayName("Should generate six characters by default, matching game.room.code-length")
    void shouldGenerateSixCharacterCodesByDefault() {
        assertThat(gameRoomProperties.getCodeLength()).isEqualTo(6);

        assertThat(generateSample()).allMatch(code -> code.length() == 6);
    }

    @Test
    @DisplayName("Should follow a reconfigured code length instead of a hardcoded six")
    void shouldFollowConfiguredCodeLength() {
        gameRoomProperties.setCodeLength(8);

        assertThat(generateSample()).allMatch(code -> code.length() == 8);
    }

    @Test
    @DisplayName("Should draw only from the unambiguous uppercase alphabet")
    void shouldUseOnlyUnambiguousAlphabet() {
        assertThat(generateSample())
                .allMatch(code -> code.matches("[" + EXPECTED_ALPHABET + "]+"))
                .allMatch(code -> code.equals(code.toUpperCase()))
                .noneMatch(code -> code.contains("0"))
                .noneMatch(code -> code.contains("O"))
                .noneMatch(code -> code.contains("1"))
                .noneMatch(code -> code.contains("I"));
    }

    @Test
    @DisplayName("Should be able to draw every character of the alphabet")
    void shouldReachEveryCharacterOfTheAlphabet() {
        Set<Character> drawn = new HashSet<>();
        generateSample().forEach(code -> code.chars().forEach(c -> drawn.add((char) c)));

        assertThat(drawn).containsExactlyInAnyOrderElementsOf(
                EXPECTED_ALPHABET.chars().mapToObj(c -> (char) c).collect(Collectors.toSet()));
    }

    @Test
    @DisplayName("Should return the first candidate when no room holds it")
    void shouldReturnFirstFreeCandidate() {
        given(roomRepository.existsByCode(anyString())).willReturn(false);

        String code = roomCodeGenerator.generateUniqueRoomCode();

        ArgumentCaptor<String> checked = ArgumentCaptor.forClass(String.class);
        verify(roomRepository).existsByCode(checked.capture());
        assertThat(checked.getValue()).isEqualTo(code);
    }

    @Test
    @DisplayName("Should redraw past taken codes and return the first free one")
    void shouldRetryUntilCodeIsFree() {
        given(roomRepository.existsByCode(anyString())).willReturn(true, true, true, true, false);

        String code = roomCodeGenerator.generateUniqueRoomCode();

        ArgumentCaptor<String> checked = ArgumentCaptor.forClass(String.class);
        verify(roomRepository, times(5)).existsByCode(checked.capture());
        assertThat(code).isEqualTo(checked.getAllValues().get(4));
        assertThat(checked.getAllValues().subList(0, 4)).doesNotContain(code);
    }

    @Test
    @DisplayName("Should give up after five collisions rather than loop")
    void shouldFailAfterFiveCollisions() {
        given(roomRepository.existsByCode(anyString())).willReturn(true);

        assertThatThrownBy(() -> roomCodeGenerator.generateUniqueRoomCode())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to generate unique room code");

        verify(roomRepository, times(5)).existsByCode(anyString());
    }

    @Test
    @DisplayName("Should reject a non-positive configured code length instead of minting empty codes")
    void shouldRejectNonPositiveCodeLength() {
        gameRoomProperties.setCodeLength(0);

        assertThatThrownBy(() -> roomCodeGenerator.generateUniqueRoomCode())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("game.room.code-length must be greater than 0");

        verifyNoInteractions(roomRepository);
    }
}
