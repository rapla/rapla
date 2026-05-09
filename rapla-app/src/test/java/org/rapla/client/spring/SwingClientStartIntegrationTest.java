package org.rapla.client.spring;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.facade.client.ClientFacade;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 009 Phase 1 acceptance test — boots the full {@link RaplaSpringBootApplication}
 * server on a random port, then boots {@link SpringRaplaClient} pointed at that
 * server, calls {@code facade.login(...)} and asserts the operator becomes connected.
 *
 * <p>This is the single test that verifies the end-to-end success criterion of the
 * "Spring-only client → Spring Boot server" architecture: no exceptions during the
 * full bean-graph wiring on either side, REST proxies use the right base URL, the
 * auth round-trip succeeds, and the {@link ClientFacade}/{@code RemoteOperator}
 * graph is in a connected state.
 *
 * <p>Uses {@code facade.login(username, password)} rather than
 * {@code clientService.start(connectInfo)} on purpose — {@code start()} also opens
 * Swing UI windows (login dialog, main app frame) which is unnecessary for the
 * acceptance check and can flake in headless CI. The login() path goes through
 * the same {@code RemoteOperator.connect()} so it exercises the full URL-plumbing
 * and authentication round-trip.
 *
 * <p>Headless mode is forced via {@code java.awt.headless} so any Swing components
 * constructed during bean wiring don't fail on a missing display.
 */
@SpringBootTest(
        classes = RaplaSpringBootApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Tag("e2e")
class SwingClientStartIntegrationTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;
    private static String savedHeadless;

    @BeforeAll
    static void setup() throws IOException
    {
        savedHeadless = System.getProperty("java.awt.headless");
        System.setProperty("java.awt.headless", "true");
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = SwingClientStartIntegrationTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in, "testdefault.xml must be on the classpath");
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @AfterAll
    static void teardown()
    {
        if (savedHeadless != null) System.setProperty("java.awt.headless", savedHeadless);
        else System.clearProperty("java.awt.headless");
        System.clearProperty("rapla.download.url");
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
    }

    @LocalServerPort
    int serverPort;

    @Test
    void clientConnectsToServerAndOperatorIsConnected() throws Exception
    {
        // Point the Swing client's StartupEnvironment.getDownloadURL() at the live test server.
        // RaplaClientServiceImpl reads this in its ctor and pushes it onto RemoteConnectionInfo.
        System.setProperty("rapla.download.url", "http://localhost:" + serverPort + "/");

        try (SpringRaplaClient client = new SpringRaplaClient())
        {
            ClientFacade facade = client.getFacade();
            assertNotNull(facade, "facade must wire");

            // Force RaplaClientServiceImpl construction — its ctor wires the RemoteOperator
            // onto the facade via setOperator(). With global lazy-init nothing else triggers
            // this, so the facade would have null operator and login() would NPE.
            assertNotNull(client.getContext().getBean(org.rapla.client.api.ClientService.class),
                    "ClientService bean must wire (this triggers operator → facade attach)");

            // login() drives RemoteOperator.connect() — full REST round-trip to /authentication.
            boolean ok = facade.login("homer", "duffs".toCharArray());
            assertTrue(ok, "homer/duffs must authenticate against the test server");
            assertTrue(facade.isSessionActive(), "facade session must be active after successful login");
        }
    }
}
