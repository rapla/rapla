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
 * The JNLP codebase is the request's own origin (scheme, Host, port as the servlet container reports
 * them), never raw {@code X-Forwarded-*} headers from the client. Whether a proxy's forwarded headers
 * are trusted is decided by Tomcat's RemoteIpValve ({@code server.tomcat.remoteip.internal-proxies}),
 * i.e. deployment configuration, not this controller. The response is {@code no-store}, so a reflected
 * Host can only reach the client that sent it. A configured {@code rapla.oauth.public-base-url} on the
 * same host overrides scheme/port (unit-tested in {@code RaplaJNLPControllerTest}).
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

    @Test
    void hostHeaderWithMarkupCharactersAnswers400() throws Exception
    {
        MvcResult result = mockMvc.perform(get("/raplaclient.jnlp").header("Host", "evil\"><x")).andReturn();
        assertEquals(400, result.getResponse().getStatus());
        assertFalse(result.getResponse().getContentAsString().contains("<x"), "Host must never be echoed");
    }

    @Test
    void ipv6HostIsEmittedBracketed() throws Exception
    {
        MvcResult result = mockMvc.perform(get("/raplaclient.jnlp").header("Host", "[::1]:8051")).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("codebase=\"http://[::1]:8051/\""), body);
    }

    private MvcResult jnlpResult(String url, String forwardedProto, String forwardedPort) throws Exception
    {
        var request = get(url);
        if (url.startsWith("https://")) request.secure(true).with(mockRequest -> {
            mockRequest.setServerPort(443);
            return mockRequest;
        });
        if (forwardedProto != null) request.header("X-Forwarded-Proto", forwardedProto);
        if (forwardedPort != null) request.header("X-Forwarded-Port", forwardedPort);
        MvcResult result = mockMvc.perform(request).andReturn();
        assertEquals(200, result.getResponse().getStatus(), () -> "jnlp must be served for " + url);
        return result;
    }

    @Test
    void codebaseIsTheRequestOrigin() throws Exception
    {
        String body = jnlpResult("https://rapla.example/raplaclient.jnlp", null, null)
                .getResponse().getContentAsString();
        assertTrue(body.contains("codebase=\"https://rapla.example/\""), body);
    }

    @Test
    void rawForwardedHeadersFromAnUntrustedClientAreNotReflected() throws Exception
    {
        String body = jnlpResult("http://localhost/raplaclient.jnlp", "https", "6666")
                .getResponse().getContentAsString();
        assertTrue(body.contains("codebase=\"http://localhost/\""), body);
        assertFalse(body.contains("6666"), () -> "forged X-Forwarded-Port reflected:\n" + body);
        assertFalse(body.contains("https://localhost"), () -> "forged X-Forwarded-Proto reflected:\n" + body);
    }

    @Test
    void jnlpResponseIsNotStoredBySharedCaches() throws Exception
    {
        MvcResult result = jnlpResult("https://rapla.example/raplaclient.jnlp", null, null);
        assertEquals("no-cache, no-store, must-revalidate", result.getResponse().getHeader("Cache-Control"));
    }
}
