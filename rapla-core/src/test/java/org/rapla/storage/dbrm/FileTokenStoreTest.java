package org.rapla.storage.dbrm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FileTokenStoreTest
{
    private static final Supplier<String> SERVER = () -> "https://rapla.example.org";

    @TempDir
    Path tempDir;

    private static FileTokenStore store(Path file)
    {
        return new FileTokenStore(file, SERVER);
    }

    private static void writeOwnerOnly(Path file, String content) throws IOException
    {
        Files.writeString(file, content);
        if (file.getFileSystem().supportedFileAttributeViews().contains("posix"))
        {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        }
    }

    @Test
    void roundTripReadsBackWhatWasWritten()
    {
        Path tokenFile = tempDir.resolve("tokens.json");
        FileTokenStore store = store(tokenFile);
        assertTrue(store.read().isEmpty(), "empty before write");

        store.tryWrite("eyJraWQ.eyJ0eXA.signature");
        Optional<String> got = store.read();
        assertTrue(got.isPresent());
        assertEquals("eyJraWQ.eyJ0eXA.signature", got.get());
        assertTrue(store.failure().isEmpty(), "a working store reports no failure");

        store.tryClear();
        assertTrue(store.read().isEmpty(), "empty after clear");
    }

    @Test
    void readReturnsEmptyOnMissingFile()
    {
        Path nonexistent = tempDir.resolve("does-not-exist.json");
        FileTokenStore store = store(nonexistent);
        assertTrue(store.read().isEmpty());
    }

    @Test
    void readReturnsEmptyOnMalformedJson() throws IOException
    {
        Path file = tempDir.resolve("garbage.json");
        writeOwnerOnly(file, "not json at all");
        FileTokenStore store = store(file);
        assertTrue(store.read().isEmpty(), "garbage doesn't crash; returns empty");
    }

    @Test
    void writeFailureDoesNotThrow()
    {
        // Path under a file (not a directory) — write will fail to create the parent.
        Path conflict = tempDir.resolve("conflict-file");
        try { Files.writeString(conflict, ""); } catch (IOException e) { /* setup */ }
        Path under = conflict.resolve("tokens.json");   // can't have a file under a file
        FileTokenStore store = store(under);
        // Must not throw; contract is catch-Throwable.
        store.tryWrite("some-token");
        assertTrue(store.read().isEmpty(), "after failed write, read still returns empty");
        assertTrue(store.failure().isPresent(), "the failed write is reported");
        assertEquals(under, store.failure().get().path());
    }

    @Test
    void clearOnNonexistentFileDoesNotThrow()
    {
        FileTokenStore store = store(tempDir.resolve("nope.json"));
        store.tryClear(); // must not throw
    }

    @Test
    void writeWithNullTokenIsNoOp()
    {
        Path file = tempDir.resolve("tokens.json");
        FileTokenStore store = store(file);
        store.tryWrite(null);
        store.tryWrite("");
        assertFalse(Files.exists(file), "null/empty tokens are silently ignored");
    }

    @Test
    void prefsSurviveTokenClear()
    {
        // PRD 029 Phase 4: tryClear() (logout) drops the token but keeps the
        // login preferences, so the next login dialog still defaults sensibly.
        FileTokenStore store = store(tempDir.resolve("tokens.json"));
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
        FileTokenStore store = store(tempDir.resolve("tokens.json"));
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
        FileTokenStore store = store(tempDir.resolve("tokens.json"));
        store.tryWritePref(TokenStore.KEY_LANGUAGE, "de");
        store.tryWritePref(TokenStore.KEY_LANGUAGE, "");
        assertTrue(store.readPref(TokenStore.KEY_LANGUAGE).isEmpty());
    }

    @Test
    void aTokenIsOnlyReturnedForTheOriginItWasWrittenFor()
    {
        AtomicReference<String> server = new AtomicReference<>("https://a.example.org");
        FileTokenStore store = new FileTokenStore(tempDir.resolve("tokens.json"), server::get);
        store.tryWrite("token-a");

        server.set("https://b.example.org");
        assertTrue(store.read().isEmpty(), "origin B must not see origin A's token");
        store.tryWrite("token-b");
        assertEquals("token-b", store.read().orElse(null));

        server.set("https://a.example.org");
        assertEquals("token-a", store.read().orElse(null), "B's write leaves A's entry alone");

        server.set("https://b.example.org");
        store.tryClear();
        assertTrue(store.read().isEmpty());
        server.set("https://a.example.org");
        assertEquals("token-a", store.read().orElse(null), "clearing B leaves A's entry alone");

        for (String other : List.of("http://a.example.org", "https://a.example.org:8443", "https://a.example.org.evil.example"))
        {
            server.set(other);
            assertTrue(store.read().isEmpty(), other + " is another origin");
        }
    }

    @Test
    void anotherStoreOnTheSameFileWithAnotherOriginDoesNotSeeTheToken()
    {
        Path file = tempDir.resolve("tokens.json");
        new FileTokenStore(file, () -> "https://a.example.org").tryWrite("token-a");
        assertTrue(new FileTokenStore(file, () -> "https://b.example.org").read().isEmpty());
        assertEquals("token-a", new FileTokenStore(file, () -> "https://a.example.org").read().orElse(null));
    }

    @Test
    void sameOriginMatchesWithTrailingSlashPathDefaultPortAndCase()
    {
        AtomicReference<String> server = new AtomicReference<>("https://rapla.example.org/");
        FileTokenStore store = new FileTokenStore(tempDir.resolve("tokens.json"), server::get);
        store.tryWrite("token");
        for (String same : List.of("https://rapla.example.org", "HTTPS://Rapla.Example.org:443/rapla/",
                "https://someone@rapla.example.org/x?y=1", "https://rapla.example.org/api"))
        {
            server.set(same);
            assertEquals("token", store.read().orElse(null), same + " is the same origin");
        }
        server.set("https://rapla.example.org:444");
        assertTrue(store.read().isEmpty(), "a non-default port is another origin");

        server.set("http://localhost:80");
        store.tryWrite("plain");
        server.set("http://localhost/api");
        assertEquals("plain", store.read().orElse(null), "default port 80 for http");
    }

    @Test
    void unknownServerUrlStoresAndReturnsNothing()
    {
        Path file = tempDir.resolve("tokens.json");
        for (String unknown : new String[] { null, "", "not a url", "localhost" })
        {
            FileTokenStore store = new FileTokenStore(file, () -> unknown);
            store.tryWrite("token");
            assertTrue(store.read().isEmpty(), "no origin, no token: " + unknown);
        }
        assertFalse(Files.exists(file), "nothing was written without an origin");
    }

    @Test
    void legacyUnkeyedTokenIsIgnoredAndDroppedOnTheNextWrite() throws IOException
    {
        Path file = tempDir.resolve("tokens.json");
        writeOwnerOnly(file, "{\"refreshToken\":\"legacy-token\",\"language\":\"de\"}\n");
        FileTokenStore store = store(file);

        assertTrue(store.read().isEmpty(), "the unkeyed token is never returned");
        assertEquals("de", store.readPref(TokenStore.KEY_LANGUAGE).orElse(null));
        assertTrue(Files.readString(file).contains("legacy-token"), "reading does not rewrite the file");

        store.tryWrite("new-token");
        assertEquals("new-token", store.read().orElse(null));
        assertFalse(Files.readString(file).contains("legacy-token"), "dropped by the write");
        assertEquals("de", store.readPref(TokenStore.KEY_LANGUAGE).orElse(null));
    }

    @Test
    void legacyUnkeyedTokenIsAlsoDroppedByAPrefWriteAndByClear() throws IOException
    {
        Path file = tempDir.resolve("tokens.json");
        writeOwnerOnly(file, "{\"refreshToken\":\"legacy-token\",\"language\":\"de\"}\n");
        store(file).tryWritePref(TokenStore.KEY_LANGUAGE, "fr");
        assertFalse(Files.readString(file).contains("legacy-token"));

        writeOwnerOnly(file, "{\"refreshToken\":\"legacy-token\",\"language\":\"de\"}\n");
        store(file).tryClear();
        assertFalse(Files.readString(file).contains("legacy-token"), "logout removes the legacy token too");
        assertEquals("de", store(file).readPref(TokenStore.KEY_LANGUAGE).orElse(null));
    }

    @Test
    @EnabledOnOs({ OS.LINUX, OS.MAC })
    void newDirectoryAndFileAreCreatedOwnerOnly() throws IOException
    {
        Path dir = tempDir.resolve(".rapla");
        Path file = dir.resolve("tokens.json");
        store(file).tryWrite("token");

        assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(dir));
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(file));
        assertEquals("token", store(file).read().orElse(null));
    }

    @Test
    @EnabledOnOs({ OS.LINUX, OS.MAC })
    void existingOwnDirectoryIsTightenedInsteadOfRefused() throws IOException
    {
        Path dir = tempDir.resolve(".rapla");
        Files.createDirectory(dir);
        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxr-xr-x"));
        Path file = dir.resolve("tokens.json");

        assertTrue(store(file).read().isEmpty());
        assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(dir), "tightened on read");

        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxr-xr-x"));
        FileTokenStore store = store(file);
        store.tryWrite("token");
        assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(dir), "tightened on write");
        assertTrue(store.failure().isEmpty());
        assertEquals("token", store(file).read().orElse(null), "and the token is on disk");
    }

    @Test
    @EnabledOnOs({ OS.LINUX, OS.MAC })
    void aStoreReadableByOthersKeepsTheTokenInMemoryAndReportsTheFailure() throws IOException
    {
        Path file = tempDir.resolve("tokens.json");
        FileTokenStore first = store(file);
        first.tryWrite("on-disk");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));

        FileTokenStore store = store(file);
        assertTrue(store.read().isEmpty(), "a token from a file others can read is not trusted");
        assertTrue(store.failure().isPresent());
        assertEquals(file, store.failure().get().path());
        assertFalse(store.failure().get().reason().isBlank());

        store.tryWrite("in-memory");
        assertEquals("in-memory", store.read().orElse(null), "kept in memory for this session");
        String content = Files.readString(file);
        assertFalse(content.contains("in-memory"), "the file is not written");
        assertEquals(PosixFilePermissions.fromString("rw-r--r--"), Files.getPosixFilePermissions(file), "and not touched");

        store.tryWritePref(TokenStore.KEY_LANGUAGE, "de");
        assertEquals("de", store.readPref(TokenStore.KEY_LANGUAGE).orElse(null));
        assertEquals(content, Files.readString(file));
        assertEquals("in-memory", store.read().orElse(null), "a pref write keeps the memory token");

        store.tryClear();
        assertTrue(store.read().isEmpty());
        assertTrue(Files.exists(file), "the insecure file is left alone");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void windowsFileIsOwnerOnlyAndAnotherReaderForcesMemory() throws IOException
    {
        Path file = tempDir.resolve(".rapla").resolve("tokens.json");
        FileTokenStore store = store(file);
        store.tryWrite("token");
        assertEquals("token", store(file).read().orElse(null));
        assertTrue(store.failure().isEmpty(), "own, profile-default ACL is acceptable");

        UserPrincipal everyone;
        try
        {
            everyone = file.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName("Everyone");
        }
        catch (IOException e)
        {
            assumeTrue(false, "no 'Everyone' account name on this Windows locale");
            return;
        }
        AclFileAttributeView view = Files.getFileAttributeView(file, AclFileAttributeView.class);
        List<AclEntry> acl = new ArrayList<>(view.getAcl());
        acl.add(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(everyone)
                .setPermissions(EnumSet.of(AclEntryPermission.READ_DATA)).build());
        view.setAcl(acl);

        FileTokenStore other = store(file);
        assertTrue(other.read().isEmpty());
        assertTrue(other.failure().isPresent());
        assertEquals(file, other.failure().get().path());
        other.tryWrite("in-memory");
        assertEquals("in-memory", other.read().orElse(null));
        assertFalse(Files.readString(file).contains("in-memory"));
    }
}
