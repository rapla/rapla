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
        FileTokenStore store = new FileTokenStore(tokenFile, null);
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
        FileTokenStore store = new FileTokenStore(nonexistent, null);
        assertTrue(store.read().isEmpty());
    }

    @Test
    void readReturnsEmptyOnMalformedJson() throws IOException
    {
        Path file = tempDir.resolve("garbage.json");
        Files.writeString(file, "not json at all");
        FileTokenStore store = new FileTokenStore(file, null);
        assertTrue(store.read().isEmpty(), "garbage doesn't crash; returns empty");
    }

    @Test
    void writeFailureDoesNotThrow()
    {
        // Path under a file (not a directory) — write will fail to create the parent.
        Path conflict = tempDir.resolve("conflict-file");
        try { Files.writeString(conflict, ""); } catch (IOException e) { /* setup */ }
        Path under = conflict.resolve("tokens.json");   // can't have a file under a file
        FileTokenStore store = new FileTokenStore(under, null);
        // Must not throw; contract is catch-Throwable.
        store.tryWrite("some-token");
        assertTrue(store.read().isEmpty(), "after failed write, read still returns empty");
    }

    @Test
    void clearOnNonexistentFileDoesNotThrow()
    {
        FileTokenStore store = new FileTokenStore(tempDir.resolve("nope.json"), null);
        store.tryClear(); // must not throw
    }

    @Test
    void writeWithNullTokenIsNoOp()
    {
        Path file = tempDir.resolve("tokens.json");
        FileTokenStore store = new FileTokenStore(file, null);
        store.tryWrite(null);
        store.tryWrite("");
        assertFalse(Files.exists(file), "null/empty tokens are silently ignored");
    }
}
