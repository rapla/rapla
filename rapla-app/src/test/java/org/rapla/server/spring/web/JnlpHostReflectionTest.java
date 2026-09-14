package org.rapla.server.spring.web;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Security audit PH3 — the unauthenticated JNLP launcher manifest must not reflect the request host: a forged Host /
 * X-Forwarded-* header would otherwise point the victim's Web Start client at an attacker's host for the jars.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class JnlpHostReflectionTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws Exception
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = JnlpHostReflectionTest.class.getResourceAsStream("/testdefault.xml"))
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

    @Autowired
    MockMvc mockMvc;

    private String jnlp(String url, String forwardedProto, String forwardedPort) throws Exception
    {
        var request = get(url);
        if (forwardedProto != null) request.header("X-Forwarded-Proto", forwardedProto);
        if (forwardedPort != null) request.header("X-Forwarded-Port", forwardedPort);
        MvcResult result = mockMvc.perform(request).andReturn();
        assertEquals(200, result.getResponse().getStatus(), () -> "jnlp must be served for " + url);
        return result.getResponse().getContentAsString();
    }

    @Test
    void forgedHostIsNotReflected() throws Exception
    {
        String body = jnlp("http://evil.example/raplaclient.jnlp", null, null);
        assertTrue(body.contains("<jnlp"), body);
        assertFalse(body.contains("evil.example"), () -> "forged host reflected:\n" + body);
    }

    @Test
    void forgedForwardedHeadersAreNotReflected() throws Exception
    {
        String body = jnlp("http://localhost/raplaclient.jnlp", "https", "6666");
        assertFalse(body.contains("6666"), () -> "forged X-Forwarded-Port reflected:\n" + body);
        // the manifest carries the static homepage link https://rapla.org — only a proto+host URL would be a reflection
        assertFalse(body.contains("https://localhost"), () -> "forged X-Forwarded-Proto reflected:\n" + body);
    }

    @Test
    void responseIsIndependentOfTheRequestHost() throws Exception
    {
        assertEquals(jnlp("http://localhost/raplaclient.jnlp", null, null),
                jnlp("http://evil.example:8443/raplaclient.jnlp", "https", "6666"),
                "the manifest must be byte-identical whatever host the request names");
    }
}
