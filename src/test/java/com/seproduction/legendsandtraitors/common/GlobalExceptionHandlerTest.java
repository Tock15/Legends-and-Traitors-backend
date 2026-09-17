package com.seproduction.legendsandtraitors.common;

import com.seproduction.legendsandtraitors.security.InvalidJwtException;
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

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.RejectingController.class)
@ActiveProfiles("test")
@Import(GlobalExceptionHandlerTest.RejectingController.class)
class GlobalExceptionHandlerTest {

    private static final String FAILED_CHECK = "JWT signature does not match";

    @RestController
    static class RejectingController {

        @GetMapping("/test/rejecting")
        void reject() {
            throw new InvalidJwtException(FAILED_CHECK);
        }
    }

    @Autowired
    private MockMvcTester mockMvc;

    private MvcTestResult getRejecting() {
        return mockMvc.get().uri("/test/rejecting").exchange();
    }

    @Test
    @DisplayName("Should answer a rejected token with 401 problem+json")
    void shouldRenderUnauthorizedProblemDetail() {
        assertThat(getRejecting())
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.detail").isEqualTo("Invalid or expired token");
    }

    @Test
    @DisplayName("Should not leak which verification step failed")
    void shouldNotLeakFailedCheck() {
        assertThat(getRejecting()).bodyJson()
                .extractingPath("$.detail").asString().doesNotContain("signature");
    }
}
