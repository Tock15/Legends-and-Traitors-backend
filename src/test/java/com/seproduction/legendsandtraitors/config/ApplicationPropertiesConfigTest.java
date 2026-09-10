package com.seproduction.legendsandtraitors.config;

import com.seproduction.legendsandtraitors.LegendsAndTraitorsBackendApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ApplicationPropertiesConfigTest {

    @Autowired
    private GameRoomProperties gameRoomProperties;

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    @DisplayName("Should inject GameRoomProperties correctly from test profile")
    void shouldLoadGameRoomProperties() {
        assertThat(gameRoomProperties).isNotNull();
        assertThat(gameRoomProperties.getCodeLength()).isEqualTo(6);
        assertThat(gameRoomProperties.getMinPlayers()).isEqualTo(2); // overridden for tests/dev
        assertThat(gameRoomProperties.getMaxPlayers()).isEqualTo(8);
        assertThat(gameRoomProperties.getTtlSeconds()).isEqualTo(1800);
        assertThat(gameRoomProperties.getKickBanDurationMinutes()).isEqualTo(5);
        assertThat(gameRoomProperties.getAfkThresholdSeconds()).isEqualTo(60);
    }

    @Test
    @DisplayName("Should inject JwtProperties correctly from test profile")
    void shouldLoadJwtProperties() {
        assertThat(jwtProperties).isNotNull();
        assertThat(jwtProperties.getSecret()).isNotBlank();
        assertThat(jwtProperties.getExpirationMs()).isEqualTo(86_400_000L);
    }

    @Test
    @DisplayName("Should fail fast on startup in prod profile when critical environment variables are missing")
    void shouldFailStartupWhenProdSecretsMissing() {
        assertThatThrownBy(() -> {
            new SpringApplicationBuilder(LegendsAndTraitorsBackendApplication.class)
                    .profiles("prod")
                    .run();
        }).isNotNull();
    }
}
