package com.seproduction.legendsandtraitors.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GameRoomPropertiesProfilesTest {

    private static final YamlPropertySourceLoader YAML = new YamlPropertySourceLoader();

    @ParameterizedTest(name = "{0} -> min-players {1}")
    @CsvSource({"default, 4", "dev, 2", "test, 2", "prod, 4"})
    @DisplayName("Should bind game.room.min-players per profile, with dev and test lowering the base 4 to 2")
    void shouldBindMinPlayersPerProfile(String profile, int expected) throws IOException {
        MutablePropertySources sources = new MutablePropertySources();
        if (!"default".equals(profile)) {
            load("application-" + profile + ".yml").forEach(sources::addLast);
        }
        load("application.yml").forEach(sources::addLast);

        GameRoomProperties properties = new Binder(ConfigurationPropertySources.from(sources))
                .bindOrCreate("game.room", GameRoomProperties.class);

        assertThat(properties.getMinPlayers()).isEqualTo(expected);
    }

    private static List<PropertySource<?>> load(String file) throws IOException {
        return YAML.load(file, new ClassPathResource(file));
    }
}
