package com.seproduction.legendsandtraitors.security;

import java.security.Principal;

/** The identity carried by a verified token. Guests have no row in PostgreSQL behind them. */
public record JwtPrincipal(String id, String displayName, boolean guest, boolean premium)
        implements Principal {

    @Override
    public String getName() {
        return id;
    }
}
