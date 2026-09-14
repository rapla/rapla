package org.rapla.server.spring;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.OAuthFlow;
import io.swagger.v3.oas.annotations.security.OAuthFlows;
import io.swagger.v3.oas.annotations.security.OAuthScope;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(
        info = @Info(
                title = "Rapla REST API",
                version = "2.1-SNAPSHOT",
                description = "Resource scheduling and event planning. Authorize via OAuth2 (Authorization Code + PKCE) using Rapla's bundled Spring Authorization Server, or paste a raw bearer JWT."
        ),
        security = {
                @SecurityRequirement(name = "oauth2"),
                @SecurityRequirement(name = "bearerAuth")
        }
)
@SecuritySchemes({
        @SecurityScheme(
                name = "oauth2",
                type = SecuritySchemeType.OAUTH2,
                flows = @OAuthFlows(
                        authorizationCode = @OAuthFlow(
                                authorizationUrl = "/oauth2/authorize",
                                tokenUrl = "/oauth2/token",
                                scopes = {
                                        @OAuthScope(name = "openid", description = "OIDC subject identity"),
                                        @OAuthScope(name = "profile", description = "User profile claims")
                                }
                        )
                )
        ),
        @SecurityScheme(
                name = "bearerAuth",
                type = SecuritySchemeType.HTTP,
                scheme = "bearer",
                bearerFormat = "JWT",
                description = "Paste a raw access token (e.g. from POST /oauth2/token grant_type=password). Manual fallback when OAuth2 isn't convenient."
        )
})
public class RaplaSpringBootApplication
{
    public static void main(String[] args)
    {
        // Route j.u.l calls from third-party libs (JDK HTTP client, JNDI,
        // some JAX-RS impls) through SLF4J/Logback so they respect the
        // logback.xml category rules and land in logs/rapla.log.
        // logback-classic's LevelChangePropagator syncs the JUL per-logger
        // levels so DEBUG-level rules surface JUL events that JUL would
        // otherwise filter out before our bridge sees them.
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        SpringApplication.run(RaplaSpringBootApplication.class, args);
    }
}
