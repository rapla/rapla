package org.rapla.storage.dbrm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileTokenStoreTest
{
    @TempDir
    Path tempDir;

    @Test
    void roundTripReadsBackWhatWasWritten()
    {
        Path tokenFile = tempDir.resolve("tokens.json");
        FileTokenStore store = new FileTokenStore(tokenFile);
        assertTrue(store.read().isEmpty(), "empty before write");

        store.tryWrite("eyJraWQ.eyJ0eXA.signature");
        Optional<String> got = store.read();
        assertTrue(got.isPresent());
        assertEquals("eyJraWQ.eyJ0eXA.signature", got.get());

        store.tryClear();
        assertTrue(store.read().isEmpty(), "empty after clear");
    }

    @Test
    void readReturnsEmptyOnMissingFile()
    {
        Path nonexistent = tempDir.resolve("does-not-exist.json");
        FileTokenStore store = new FileTokenStore(nonexistent);
        assertTrue(store.read().isEmpty());
    }

    @Test
    void readReturnsEmptyOnMalformedJson() throws IOException
    {
        Path file = tempDir.resolve("garbage.json");
        Files.writeString(file, "not json at all");
        FileTokenStore store = new FileTokenStore(file);
        assertTrue(store.read().isEmpty(), "garbage doesn't crash; returns empty");
    }

    @Test
    void writeFailureDoesNotThrow()
    {
        // Path under a file (not a directory) — write will fail to create the parent.
        Path conflict = tempDir.resolve("conflict-file");
        try { Files.writeString(conflict, ""); } catch (IOException e) { /* setup */ }
        Path under = conflict.resolve("tokens.json");   // can't have a file under a file
        FileTokenStore store = new FileTokenStore(under);
        // Must not throw; contract is catch-Throwable.
        store.tryWrite("some-token");
        assertTrue(store.read().isEmpty(), "after failed write, read still returns empty");
    }

    @Test
    void clearOnNonexistentFileDoesNotThrow()
    {
        FileTokenStore store = new FileTokenStore(tempDir.resolve("nope.json"));
        store.tryClear(); // must not throw
    }

    @Test
    void writeWithNullTokenIsNoOp()
    {
        Path file = tempDir.resolve("tokens.json");
        FileTokenStore store = new FileTokenStore(file);
        store.tryWrite(null);
        store.tryWrite("");
        assertFalse(Files.exists(file), "null/empty tokens are silently ignored");
    }

    @Test
    void prefsSurviveTokenClear()
    {
        // PRD 029 Phase 4: tryClear() (logout) drops the token but keeps the
        // login preferences, so the next login dialog still defaults sensibly.
        FileTokenStore store = new FileTokenStore(tempDir.resolve("tokens.json"));
        store.tryWritePref(TokenStore.KEY_LANGUAGE, "de");
        store.tryWrite("eyJ.refresh.tok");
        assertEquals("eyJ.refresh.tok", store.read().orElse(null));
        assertEquals("de", store.readPref(TokenStore.KEY_LANGUAGE).orElse(null));

        store.tryClear();
        assertTrue(store.read().isEmpty(), "token gone after clear");
        assertEquals("de", store.readPref(TokenStore.KEY_LANGUAGE).orElse(null),
                "language preference survives logout");
    }

    @Test
    void tokenAndPrefWritesDoNotClobberEachOther()
    {
        FileTokenStore store = new FileTokenStore(tempDir.resolve("tokens.json"));
        store.tryWrite("tok1");
        store.tryWritePref(TokenStore.KEY_LOGIN_METHOD, "keycloak");
        store.tryWritePref(TokenStore.KEY_LANGUAGE, "fr");
        store.tryWrite("tok2");   // re-writing the token must keep the prefs

        assertEquals("tok2", store.read().orElse(null));
        assertEquals("keycloak", store.readPref(TokenStore.KEY_LOGIN_METHOD).orElse(null));
        assertEquals("fr", store.readPref(TokenStore.KEY_LANGUAGE).orElse(null));
    }

    @Test
    void emptyPrefValueClearsThePref()
    {
        FileTokenStore store = new FileTokenStore(tempDir.resolve("tokens.json"));
        store.tryWritePref(TokenStore.KEY_LANGUAGE, "de");
        store.tryWritePref(TokenStore.KEY_LANGUAGE, "");
        assertTrue(store.readPref(TokenStore.KEY_LANGUAGE).isEmpty());
    }
}
