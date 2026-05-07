package org.rapla.server.spring;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.RaplaKeyStorage;
import org.rapla.server.RemoteSession;
import org.rapla.server.ServerServiceContainer;
import org.rapla.server.internal.RaplaAuthentificationService;
import org.rapla.server.internal.TokenHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = RaplaSpringBootApplication.class)
class ServerServiceIntegrationTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ServerServiceIntegrationTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in, "testdefault.xml must be on the classpath");
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
    }

    @Autowired
    ServerServiceContainer serverServiceContainer;

    @Autowired
    RaplaKeyStorage raplaKeyStorage;

    @Autowired
    TokenHandler tokenHandler;

    @Autowired
    RaplaAuthentificationService raplaAuthentificationService;

    @Autowired
    RemoteSession remoteSession;

    @Test
    void serverServiceContainerStarts()
    {
        assertNotNull(serverServiceContainer);
    }

    @Test
    void authChainResolves()
    {
        assertNotNull(raplaKeyStorage);
        assertNotNull(tokenHandler);
        assertNotNull(raplaAuthentificationService);
        assertNotNull(remoteSession);
    }
}
