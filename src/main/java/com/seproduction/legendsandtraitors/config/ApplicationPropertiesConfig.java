package com.seproduction.legendsandtraitors.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables and registers type-safe property classes into the Spring application context.
 */
@Configuration
@EnableConfigurationProperties({
        GameRoomProperties.class,
        JwtProperties.class
})
public class ApplicationPropertiesConfig {
}
