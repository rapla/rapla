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
                                authorizationUrl = "/rapla/oauth2/authorize",
                                tokenUrl = "/rapla/oauth2/token",
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
                description = "Paste a raw access token (e.g. from POST /auth/login). Manual fallback when OAuth2 isn't convenient."
        )
})
public class RaplaSpringBootApplication
{
    public static void main(String[] args)
    {
        SpringApplication.run(RaplaSpringBootApplication.class, args);
    }
}
