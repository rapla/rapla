package org.rapla.server.spring.oauth.external;

public enum ExternalProviderId
{
    MICROSOFT("microsoft"),
    GOOGLE("google");

    private final String id;

    ExternalProviderId(String id) { this.id = id; }

    public String id() { return id; }
}
