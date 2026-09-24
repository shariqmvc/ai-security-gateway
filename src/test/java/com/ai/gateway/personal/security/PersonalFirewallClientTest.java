package com.ai.gateway.personal.security;

import com.ai.gateway.personal.security.firewall.PersonalFirewallClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class PersonalFirewallClientTest {

    @Test
    void failClosedWhenFirewallIsUnavailable() {
        PersonalFirewallProperties properties = new PersonalFirewallProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://localhost:1");
        properties.setConnectTimeoutMs(100);
        properties.setReadTimeoutMs(100);
        properties.setFailOpen(false);

        PersonalFirewallClient client = new PersonalFirewallClient(properties);

        assertThrows(
                PersonalFirewallUnavailableException.class,
                () -> client.detect(
                        java.util.UUID.randomUUID(), "hello"));
    }
}
