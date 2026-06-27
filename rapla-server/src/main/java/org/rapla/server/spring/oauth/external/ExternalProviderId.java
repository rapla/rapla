package org.rapla.server.spring.oauth.external;

import java.util.Optional;

/**
 * The <em>type</em> of an external OIDC provider (PRD 089). Distinct from a
 * provider's {@code registrationId} (the arbitrary map key under
 * {@code rapla.oauth.external} — e.g. {@code dhbw}, {@code keycloak-mos}); a
 * deployment may register several providers of the same type. The type selects
 * the per-type URL derivation and defaults applied in
 * {@link ProviderDef#toProviderConfig(String)} and the one type-specific
 * behaviour left in {@code RaplaClientRegistrationConfig} (the PRD 072 DHBW
 * legacy callback, gated on {@code type == KEYCLOAK}).
 */
public enum ExternalProviderId
{
    MICROSOFT("microsoft"),
    GOOGLE("google"),
    KEYCLOAK("keycloak");

    private final String id;

    ExternalProviderId(String id) { this.id = id; }

    public String id() { return id; }

    /**
     * Parse a {@code type:} value (case-insensitive). Used both for the explicit
     * {@code type:} field and for inferring the type from a map key when
     * {@code type:} is omitted (back-compat for the legacy fixed-key configs).
     */
    public static Optional<ExternalProviderId> parse(String value)
    {
        if (value == null) return Optional.empty();
        String v = value.trim().toLowerCase();
        for (ExternalProviderId t : values())
        {
            if (t.id.equals(v)) return Optional.of(t);
        }
        return Optional.empty();
    }
}
