package com.seproduction.legendsandtraitors.config;

import com.seproduction.legendsandtraitors.security.JwtAuthenticationFilter;
import com.seproduction.legendsandtraitors.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
public class JwtFilterConfig {

    // Registered here rather than as a @Component so it never runs on /ws or an ERROR dispatch.
    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {

        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(
                new JwtAuthenticationFilter(jwtTokenProvider, handlerExceptionResolver));
        registration.addUrlPatterns("/api/*");
        return registration;
    }
}
