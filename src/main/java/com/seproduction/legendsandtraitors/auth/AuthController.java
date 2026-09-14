package com.seproduction.legendsandtraitors.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
class AuthController {

    private final GuestAuthService guestAuthService;

    /** Takes no body — the identity is generated server-side, not requested. */
    @PostMapping("/guest")
    GuestAuthResponse createGuestSession() {
        return guestAuthService.createGuestSession();
    }
}
