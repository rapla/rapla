package org.rapla.server.spring.oauth.external;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * External OIDC providers, keyed by {@code registrationId} (PRD 036 Phase 3).
 * A deployment may register any number of providers of any type — e.g. two
 * Keycloaks (DHBW Mosbach + a second realm) or a second Google app — each with
 * its own {@code /login/oauth2/code/{registrationId}} callback. Default-empty —
 * a fresh rapla deployment with no external config behaves exactly as before.
 *
 * <p>The map value {@link ProviderDef} carries a {@code type} discriminator
 * (microsoft/google/keycloak); when omitted the type is inferred from the key
 * (back-compat for the legacy fixed-key configs). See PRD 036.
 */
@ConfigurationProperties(prefix = "rapla.oauth")
public class ExternalProvidersProperties
{
    /** registrationId → provider definition. Bound from {@code rapla.oauth.external.*}. */
    private Map<String, ProviderDef> external = new LinkedHashMap<>();

    public Map<String, ProviderDef> getExternal() { return external; }
    public void setExternal(Map<String, ProviderDef> external) { this.external = external; }

    public List<ProviderConfig> enabledProviders()
    {
        List<ProviderConfig> out = new ArrayList<>(external.size());
        for (Map.Entry<String, ProviderDef> e : external.entrySet())
        {
            ProviderDef def = e.getValue();
            if (def != null && def.isEnabled())
            {
                out.add(def.toProviderConfig(e.getKey()));
            }
        }
        return out;
    }

    public Optional<ProviderConfig> byIssuer(String issuer)
    {
        if (issuer == null) return Optional.empty();
        return enabledProviders().stream()
                .filter(p -> p.matchesIssuer(issuer))
                .findFirst();
    }

    public Optional<ProviderConfig> byId(String id)
    {
        if (id == null) return Optional.empty();
        return enabledProviders().stream()
                .filter(p -> id.equals(p.id()))
                .findFirst();
    }
}
