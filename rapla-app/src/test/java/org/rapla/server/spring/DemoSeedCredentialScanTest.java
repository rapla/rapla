package org.rapla.server.spring;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * PRD 118 D8-9 — a demo seed is copied over the data file by the nightly reset, so anything
 * credential-shaped in it would survive every reset: the RSA signing key and its public half
 * ({@code org.rapla.crypto.*}, also the API-key slots), refresh sessions ({@code org.rapla.auth.session}),
 * remember-me tokens ({@code org.rapla.auth.rememberMeTokens}) and encrypted login infos
 * ({@code org.rapla.server.exchangeuser}). Prefix match on the preference entry key.
 */
class DemoSeedCredentialScanTest
{
    static final List<String> CREDENTIAL_PREFIXES = List.of("org.rapla.crypto", "org.rapla.auth.session",
            "org.rapla.auth.rememberMeTokens", "org.rapla.server.exchangeuser");

    static final Pattern ENTRY_KEY = Pattern.compile("<rapla:entry\\s+key=\"([^\"]+)\"");

    static List<String> credentialEntries(String xml)
    {
        List<String> hits = new ArrayList<>();
        Matcher m = ENTRY_KEY.matcher(xml);
        while (m.find())
        {
            String key = m.group(1);
            if (CREDENTIAL_PREFIXES.stream().anyMatch(key::startsWith))
            {
                hits.add(key);
            }
        }
        return hits;
    }

    /** The scrub a seed author applies: drops every self-closing credential entry. */
    static String scrub(String xml)
    {
        StringBuilder out = new StringBuilder();
        for (String line : xml.split("\n", -1))
        {
            Matcher m = ENTRY_KEY.matcher(line);
            boolean credential = m.find() && CREDENTIAL_PREFIXES.stream().anyMatch(m.group(1)::startsWith);
            if (!credential)
            {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    static String fixture() throws IOException
    {
        try (InputStream in = DemoSeedCredentialScanTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void noDemoSeedCarriesCredentials() throws IOException
    {
        Path cwd = Path.of("").toAbsolutePath();
        Path root = Files.isDirectory(cwd.resolve("rapla-app")) ? cwd : cwd.getParent();
        assertEquals(true, Files.isDirectory(root.resolve("rapla-app")), "repository root not found from " + cwd);
        List<Path> seeds = new ArrayList<>();
        Path data = root.resolve("data");
        if (Files.isDirectory(data))
        {
            try (Stream<Path> files = Files.list(data))
            {
                files.filter(p -> p.getFileName().toString().matches("demo-.*\\.xml")).forEach(seeds::add);
            }
        }
        Path docs = root.resolve("docs/demo");
        if (Files.isDirectory(docs))
        {
            try (Stream<Path> files = Files.walk(docs))
            {
                files.filter(p -> p.toString().endsWith(".xml")).forEach(seeds::add);
            }
        }
        List<String> failures = new ArrayList<>();
        for (Path seed : seeds)
        {
            List<String> hits = credentialEntries(Files.readString(seed));
            if (!hits.isEmpty())
            {
                failures.add(root.relativize(seed) + " " + hits);
            }
        }
        assertEquals(List.of(), failures, "scanned " + seeds.size() + " demo seed file(s)");
    }

    @Test
    void theScanFindsEveryCredentialPrefixAndTheScrubRemovesThem() throws IOException
    {
        String keyed = fixture();
        assertEquals(List.of("org.rapla.crypto.server.privateKey", "org.rapla.crypto.publicKey"), credentialEntries(keyed));
        String synthetic = """
                <rapla:entry key="org.rapla.auth.session" value="x"/>
                <rapla:entry key="org.rapla.auth.rememberMeTokens" value="x"/>
                <rapla:entry key="org.rapla.server.exchangeuser" value="x"/>
                <rapla:entry key="org.rapla.crypto.server.refreshToken" value="x"/>
                <rapla:entry key="org.rapla.plugin.autoexport.show_calendar_list_in_html_menu" value="true"/>
                """;
        assertEquals(4, credentialEntries(synthetic).size());
        assertEquals(List.of(), credentialEntries(scrub(keyed)));
        assertEquals(List.of(), credentialEntries(scrub(synthetic)));
        assertEquals(1, ENTRY_KEY.matcher(scrub(synthetic)).results().count());
    }
}
