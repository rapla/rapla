package org.rapla.server.spring.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Locks in the fix for the JSON-quoting regression in POST /api/urlencryption.
 *
 * <p>The Spring HTTP-interface proxy used to send the encrypt argument as
 * {@code application/json}, so {@code @RequestBody String plain} received the
 * JSON-encoded string with surrounding quotes, producing a wrong ciphertext
 * that the decryptor could never validate. Adding {@code contentType = "text/plain"}
 * to the {@code @PostExchange} makes the proxy send the raw string AND restricts
 * the server to only accept {@code text/plain} (any {@code application/json}
 * caller gets 415 instead of silently wrong ciphertext).
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class UrlEncryptionControllerIntegrationTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = UrlEncryptionControllerIntegrationTest.class.getResourceAsStream("/testdefault.xml"))
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
    MockMvc mockMvc;

    @Test
    void textPlainBodyEncryptsCorrectly() throws Exception
    {
        String token = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");

        MvcResult result = mockMvc.perform(post("/api/urlencryption")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("user=homer&file=Export")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String ciphertext = result.getResponse().getContentAsString();
        assertTrue(ciphertext.contains("&salt="), "ciphertext must contain the &salt= separator");
        assertTrue(ciphertext.length() > 20, "ciphertext must be non-trivial");
    }

    @Test
    void v2AlgoProducesDeterministicGcmCiphertext() throws Exception
    {
        String token = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");

        String first = encryptV2(token);
        String second = encryptV2(token);

        // PRD 071 H3: new exports use AES-256-GCM, marked with the v2: prefix, no legacy &salt=
        assertTrue(first.startsWith("v2:"), "v2 ciphertext must carry the v2: marker: " + first);
        assertFalse(first.contains("&salt="), "v2 ciphertext must not use the legacy salt param");
        // deterministic → the export URL stays byte-stable across regenerations
        assertEquals(first, second, "v2 ciphertext must be deterministic so the URL doesn't churn");
    }

    private String encryptV2(String token) throws Exception
    {
        return mockMvc.perform(post("/api/urlencryption")
                        .param("algo", "v2")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("user=homer&file=Export")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void jsonQuotedBodyIsRejectedWith415() throws Exception
    {
        String token = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");

        mockMvc.perform(post("/api/urlencryption")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("\"user=homer&file=Export\"")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnsupportedMediaType());
    }
}
