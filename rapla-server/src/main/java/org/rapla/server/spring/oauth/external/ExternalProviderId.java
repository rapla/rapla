package org.rapla.server.spring.oauth.external;

public enum ExternalProviderId
{
    MICROSOFT("microsoft"),
    GOOGLE("google"),
    KEYCLOAK("keycloak");

    private final String id;

    ExternalProviderId(String id) { this.id = id; }

    public String id() { return id; }
}
