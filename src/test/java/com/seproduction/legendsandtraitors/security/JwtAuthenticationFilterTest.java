package com.seproduction.legendsandtraitors.security;

import com.seproduction.legendsandtraitors.config.ApplicationPropertiesConfig;
import com.seproduction.legendsandtraitors.config.JwtFilterConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(controllers = JwtAuthenticationFilterTest.ProbeController.class)
@ActiveProfiles("test")
@Import({JwtAuthenticationFilterTest.ProbeController.class, JwtFilterConfig.class,
        JwtTokenProvider.class, ApplicationPropertiesConfig.class})
class JwtAuthenticationFilterTest {

    private static final String USER_ID = "guest_948201";
    private static final String DISPLAY_NAME = "Guest948201";
    private static final String ANONYMOUS = "anonymous";

    @RestController
    static class ProbeController {

        @GetMapping("/api/probe")
        String whoAmI(Principal principal) {
            return principal == null ? ANONYMOUS : principal.getName();
        }
    }

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private MvcTestResult probeWith(String authorization) {
        if (authorization == null) {
            return mockMvc.get().uri("/api/probe").exchange();
        }
        return mockMvc.get().uri("/api/probe").header("Authorization", authorization).exchange();
    }

    @Test
    @DisplayName("Should let a request carrying no token through unauthenticated")
    void shouldPassThroughWithoutToken() {
        assertThat(probeWith(null)).hasStatusOk().hasBodyTextEqualTo(ANONYMOUS);
    }

    @Test
    @DisplayName("Should ignore an Authorization header that is not a Bearer token")
    void shouldIgnoreNonBearerHeader() {
        assertThat(probeWith("Basic dXNlcjpwYXNz")).hasStatusOk().hasBodyTextEqualTo(ANONYMOUS);
    }

    @Test
    @DisplayName("Should bind the token identity as the request principal")
    void shouldBindPrincipalFromToken() {
        String token = jwtTokenProvider.issueToken(USER_ID, DISPLAY_NAME, true, false);

        assertThat(probeWith("Bearer " + token)).hasStatusOk().hasBodyTextEqualTo(USER_ID);
    }

    @Test
    @DisplayName("Should reject a present but invalid token with the same 401 problem+json as MVC")
    void shouldRejectInvalidToken() {
        assertThat(probeWith("Bearer not-a-jwt"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.detail").isEqualTo("Invalid or expired token");
    }

    @Test
    @DisplayName("Should reject an empty Bearer token rather than treat it as anonymous")
    void shouldRejectEmptyBearerToken() {
        assertThat(probeWith("Bearer   ")).hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
