package com.seproduction.legendsandtraitors.security;

/** The identity carried by a verified token. Guests have no row in PostgreSQL behind them. */
public record JwtPrincipal(String id, String displayName, boolean guest, boolean premium) {
}
