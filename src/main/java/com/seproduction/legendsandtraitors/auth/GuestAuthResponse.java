package com.seproduction.legendsandtraitors.auth;

public record GuestAuthResponse(String token, UserDto user) {
}
