package com.ai.gateway.personal.security;

/** Raised when the fail-closed Personal ML firewall cannot be reached. */
public class PersonalFirewallUnavailableException extends RuntimeException {

    public PersonalFirewallUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
