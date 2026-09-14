package org.rapla.client.spring;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.storage.dbrm.LoginCredentials;
import org.rapla.storage.dbrm.OAuth2PasswordLogin;
import org.rapla.storage.dbrm.RemoteConnectionInfo;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for the empty-401-body bug on the Swing login dialog.
 *
 * <p>Spring 7's {@code SimpleClientHttpRequest} unconditionally enables
 * HttpURLConnection streaming mode, which causes the JDK to discard the
 * error-stream body on 401 responses (HttpRetryException territory).
 * The user sees a generic "401" instead of the localised "Login failed!"
 * the server actually sent.
 *
 * <p>The fix is {@link BufferingHttpUrlConnectionRequestFactory}, wired in
 * {@link ClientProxyConfig}. This test boots the real Spring context for
 * ClientProxyConfig and exercises the actual proxy bean against a live
 * server, so a future revert of either piece reopens the regression.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("e2e")
class BadLoginErrorMessageTest
{
    @TempDir static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void setup() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = BadLoginErrorMessageTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in);
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
    }

    @LocalServerPort int port;

    @Test
    void badLoginPreservesServerMessageBody()
    {
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(ClientProxyConfig.class))
        {
            ctx.getBean(RemoteConnectionInfo.class).setServerURL("http://localhost:" + port + "/api");
            OAuth2PasswordLogin passwordLogin = ctx.getBean(OAuth2PasswordLogin.class);

            // PRD 029 Phase 5: dropped RemoteAuthentificationService — OAuth2PasswordLogin
            // is the concrete seam now. Bad credentials surface as RaplaSecurityException
            // (mapped at the seam from HTTP 400/401), with the server's i18n'd "Login failed"
            // message preserved on the cause for visibility in logs.
            org.rapla.storage.RaplaSecurityException ex = assertThrows(
                    org.rapla.storage.RaplaSecurityException.class,
                    () -> passwordLogin.login(new LoginCredentials("admin", "wrongpw".toCharArray())),
                    "bad creds must surface as RaplaSecurityException");
            assertTrue(ex.getMessage().contains("Login failed"),
                    "seam must preserve the server's localised 'Login failed' message (got: '" + ex.getMessage() + "')");
        }
    }
}
