package com.seproduction.legendsandtraitors.auth;

import com.seproduction.legendsandtraitors.config.ApplicationPropertiesConfig;
import com.seproduction.legendsandtraitors.security.JwtPrincipal;
import com.seproduction.legendsandtraitors.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(AuthController.class)
@ActiveProfiles("test")
@Import({GuestAuthService.class, JwtTokenProvider.class, ApplicationPropertiesConfig.class})
class AuthControllerTest {

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private MvcTestResult postGuest() {
        return mockMvc.post()
                .uri("/api/auth/guest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .exchange();
    }

    private GuestAuthResponse bodyOf(MvcTestResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), GuestAuthResponse.class);
    }

    @Test
    @DisplayName("Should return 200 with a guest identity and a token this service signed")
    void shouldReturnGuestSession() throws Exception {
        MvcTestResult result = postGuest();

        assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);

        GuestAuthResponse body = bodyOf(result);
        assertThat(body.user().id()).matches("guest_[0-9]{6}");
        assertThat(body.user().displayName()).isEqualTo("Guest" + body.user().id().substring("guest_".length()));
        assertThat(body.user().guest()).isTrue();
        assertThat(body.user().premium()).isFalse();

        JwtPrincipal principal = jwtTokenProvider.parse(body.token());
        assertThat(principal.id()).isEqualTo(body.user().id());
        assertThat(principal.displayName()).isEqualTo(body.user().displayName());
        assertThat(principal.guest()).isTrue();
        assertThat(principal.premium()).isFalse();
    }

    @Test
    @DisplayName("Should name the boolean flags isGuest and isPremium on the wire")
    void shouldExposeFlagsWithIsPrefix() {
        assertThat(postGuest()).hasStatusOk()
                .bodyJson()
                .hasPath("$.token")
                .hasPath("$.user.displayName")
                .doesNotHavePath("$.user.guest")
                .doesNotHavePath("$.user.premium")
                .extractingPath("$.user.isGuest").isEqualTo(true);

        assertThat(postGuest()).bodyJson().extractingPath("$.user.isPremium").isEqualTo(false);
    }

    @Test
    @DisplayName("Should mint a distinct identity and token on each call")
    void shouldMintDistinctIdentityPerCall() throws Exception {
        GuestAuthResponse first = bodyOf(postGuest());
        GuestAuthResponse second = bodyOf(postGuest());

        assertThat(first.user().id()).isNotEqualTo(second.user().id());
        assertThat(first.token()).isNotEqualTo(second.token());
    }

    @Test
    @DisplayName("Should accept the request without a body")
    void shouldAcceptRequestWithoutBody() {
        assertThat(mockMvc.post().uri("/api/auth/guest").exchange()).hasStatusOk();
    }
}
