package org.rapla.storage.dbfile;

import org.junit.jupiter.api.Test;
import org.rapla.entities.User;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.entities.storage.StoredArtifact;
import org.rapla.entities.storage.internal.StoredArtifactImpl;
import org.rapla.test.util.FacadeTestSupport;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 098 Phase 1 — StoredArtifact round-trip over the FileOperator (data.xml):
 * store via the generic dispatch path, read back via {@link org.rapla.storage.CachableStorageOperator#getStoredArtifacts()},
 * survive a reload (file re-read), upsert by natural key, and delete.
 */
public class StoredArtifactFileRoundTripTest extends FacadeTestSupport
{
    private static final String BODY = "query leihschein @view(title: \"Leihschein <&> äöü\") { reservations { name } }";
    private static final String METADATA = "{\"isPublic\":true,\"groups\":[]}";

    private StoredArtifactImpl newArtifact(String kind, String name, String body)
    {
        StoredArtifactImpl artifact = new StoredArtifactImpl(kind, name);
        artifact.setBody(body);
        artifact.setMetadata(METADATA);
        artifact.setCreateDate(LocalDateTime.of(2026, 7, 8, 12, 0));
        artifact.setLastChanged(LocalDateTime.of(2026, 7, 8, 12, 0));
        return artifact;
    }

    private User admin() throws Exception
    {
        return operator.getUser("admin");
    }

    private Optional<StoredArtifact> find(String id) throws Exception
    {
        return operator.getStoredArtifacts().stream().filter(a -> a.getId().equals(id)).findFirst();
    }

    @Test
    public void storeReloadAndReadBack() throws Exception
    {
        StoredArtifactImpl artifact = newArtifact(StoredArtifact.KIND_VIEW, "leihschein", BODY);
        operator.storeAndRemove(List.of(artifact), Collections.emptyList(), admin());

        Optional<StoredArtifact> stored = find("VIEW:leihschein");
        assertTrue(stored.isPresent(), "artifact readable directly after store");

        operator.reload();

        Optional<StoredArtifact> reloaded = find("VIEW:leihschein");
        assertTrue(reloaded.isPresent(), "artifact survives file reload");
        StoredArtifact a = reloaded.get();
        assertEquals(StoredArtifact.KIND_VIEW, a.getKind());
        assertEquals("leihschein", a.getName());
        assertEquals(BODY, a.getBody(), "body round-trips XML-encoded special chars");
        assertEquals(METADATA, a.getMetadata());
        assertNotNull(a.getCreateDate());
    }

    @Test
    public void upsertByNaturalKeyOverwrites() throws Exception
    {
        operator.storeAndRemove(List.of(newArtifact(StoredArtifact.KIND_TEMPLATE, "doc", "v1")), Collections.emptyList(), admin());
        operator.storeAndRemove(List.of(newArtifact(StoredArtifact.KIND_TEMPLATE, "doc", "v2")), Collections.emptyList(), admin());

        operator.reload();

        List<StoredArtifact> matching = operator.getStoredArtifacts().stream()
                .filter(a -> a.getId().equals("TEMPLATE:doc")).toList();
        assertEquals(1, matching.size(), "same natural key -> single row");
        assertEquals("v2", matching.get(0).getBody());
    }

    @Test
    public void sameNameDifferentKindCoexist() throws Exception
    {
        operator.storeAndRemove(List.of(
                newArtifact(StoredArtifact.KIND_VIEW, "leihschein", "query"),
                newArtifact(StoredArtifact.KIND_TEMPLATE, "leihschein", "<div/>")), Collections.emptyList(), admin());

        operator.reload();

        assertTrue(find("VIEW:leihschein").isPresent());
        assertTrue(find("TEMPLATE:leihschein").isPresent());
    }

    @Test
    public void deleteRemovesFromStoreAndFile() throws Exception
    {
        StoredArtifactImpl artifact = newArtifact(StoredArtifact.KIND_CSS, "print", "@page { margin: 2cm; }");
        operator.storeAndRemove(List.of(artifact), Collections.emptyList(), admin());
        assertTrue(find("CSS:print").isPresent());

        operator.storeAndRemove(Collections.emptyList(),
                List.of(new ReferenceInfo<>("CSS:print", StoredArtifact.class)), admin());
        assertFalse(find("CSS:print").isPresent(), "removed from side map");

        operator.reload();
        assertFalse(find("CSS:print").isPresent(), "removed from data.xml");
    }

    @Test
    public void artifactsNeverEnterTheEntityCache() throws Exception
    {
        operator.storeAndRemove(List.of(newArtifact(StoredArtifact.KIND_VIEW, "cachecheck", "q")), Collections.emptyList(), admin());
        assertTrue(operator.tryResolve(new ReferenceInfo<>("VIEW:cachecheck", StoredArtifact.class)) == null,
                "artifact must not be resolvable from the LocalCache");
    }
}
