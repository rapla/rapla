package org.rapla.server.spring.graphql;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.rapla.entities.User;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.entities.storage.StoredArtifact;
import org.rapla.entities.storage.internal.StoredArtifactImpl;
import org.rapla.framework.RaplaException;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.StorageOperator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * PRD 098 — generic CRUD over the server artifact store ({@link StoredArtifact}).
 *
 * <p>Reads are read-through against the store behind a process-local 10 s TTL snapshot that is
 * invalidated on every local write (same-pod read-your-own-writes; other pods are at most one
 * TTL stale — the same bound the update-history poll gives every other entity). Bodies larger
 * than 1 MiB are held metadata-only in the snapshot and fetched from the store on access.
 *
 * <p>Writes are admin-only (D6) — artifacts are application-scoped content collectively owned
 * by all admins; this method is the single authorization seam. Rename is not an operation:
 * identity is the natural key {@code kind:name} (OQ4), "renaming" = save-new + delete-old.
 */
@Component
@EnableConfigurationProperties(RaplaArtifactProperties.class)
public class ArtifactCatalogService
{
    private static final long CACHE_TTL_MILLIS = 10_000;
    private static final long CACHE_BODY_LIMIT_BYTES = 1024 * 1024;

    private final CachableStorageOperator operator;
    private final RaplaArtifactProperties properties;

    private volatile Snapshot snapshot;

    private record Snapshot(List<StoredArtifact> artifacts, Set<String> strippedIds, long loadedAtMillis) { }

    public ArtifactCatalogService(StorageOperator operator, RaplaArtifactProperties properties)
    {
        this.operator = (CachableStorageOperator) operator;
        this.properties = properties;
    }

    /** All application-scoped artifacts of the given kind (bodies may be stripped for >1 MiB entries). */
    public List<StoredArtifact> list(String kind)
    {
        return snapshot().artifacts().stream().filter(a -> kind.equals(a.getKind())).collect(Collectors.toList());
    }

    /** Find by natural key; the returned artifact always carries its full body. */
    public Optional<StoredArtifact> find(String kind, String name)
    {
        final String id = StoredArtifact.createId(kind, name);
        final Snapshot snap = snapshot();
        final Optional<StoredArtifact> cached = snap.artifacts().stream().filter(a -> a.getId().equals(id)).findFirst();
        if (cached.isPresent() && snap.strippedIds().contains(id))
        {
            return readThrough(id);
        }
        return cached;
    }

    /** Create or overwrite (upsert by natural key). Admin-only. */
    public void save(String kind, String name, String body, String metadata, User caller) throws RaplaException
    {
        checkWrite(caller);
        checkBodySize(body);
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        final StoredArtifactImpl artifact = new StoredArtifactImpl(kind, name);
        artifact.setBody(body);
        artifact.setMetadata(metadata);
        artifact.setLastChanged(now);
        artifact.setLastChangedBy(caller);
        artifact.setCreateDate(find(kind, name).map(StoredArtifact::getCreateDate).orElse(now));
        operator.storeAndRemove(List.of(artifact), List.of(), caller);
        invalidate();
    }

    /** Delete by natural key. Admin-only. Returns false when the artifact does not exist. */
    public boolean delete(String kind, String name, User caller) throws RaplaException
    {
        checkWrite(caller);
        final String id = StoredArtifact.createId(kind, name);
        if (find(kind, name).isEmpty())
        {
            return false;
        }
        operator.storeAndRemove(List.of(), List.of(new ReferenceInfo<>(id, StoredArtifact.class)), caller);
        invalidate();
        return true;
    }

    private void checkWrite(User caller) throws RaplaSecurityException
    {
        if (caller == null || !caller.isAdmin())
        {
            throw new RaplaSecurityException("Only admins may modify stored artifacts");
        }
    }

    private void checkBodySize(String body) throws RaplaException
    {
        final long maxBytes = properties.getMaxBodySize().toBytes();
        final long actual = body == null ? 0 : body.getBytes(StandardCharsets.UTF_8).length;
        if (actual > maxBytes)
        {
            throw new RaplaException("Artifact body of " + actual + " bytes exceeds rapla.artifact.max-body-size ("
                    + maxBytes + " bytes) — attachment-shaped content does not belong in the artifact store");
        }
    }

    private void invalidate()
    {
        snapshot = null;
    }

    private Snapshot snapshot()
    {
        Snapshot snap = snapshot;
        final long nowMillis = System.currentTimeMillis();
        if (snap != null && nowMillis - snap.loadedAtMillis() < CACHE_TTL_MILLIS)
        {
            return snap;
        }
        snap = load(nowMillis);
        snapshot = snap;
        return snap;
    }

    private Snapshot load(long nowMillis)
    {
        try
        {
            final Collection<StoredArtifact> all = operator.getStoredArtifacts();
            final List<StoredArtifact> applicationScoped = new ArrayList<>();
            final Set<String> stripped = new java.util.HashSet<>();
            for (StoredArtifact artifact : all)
            {
                if (artifact.getOwnerRef() != null)
                {
                    continue;   // application catalog never surfaces owned rows (D7)
                }
                final String body = artifact.getBody();
                if (body != null && body.getBytes(StandardCharsets.UTF_8).length > CACHE_BODY_LIMIT_BYTES)
                {
                    final StoredArtifactImpl strippedClone = ((StoredArtifactImpl) artifact).clone();
                    strippedClone.setBody(null);
                    applicationScoped.add(strippedClone);
                    stripped.add(artifact.getId());
                }
                else
                {
                    applicationScoped.add(artifact);
                }
            }
            return new Snapshot(List.copyOf(applicationScoped), Set.copyOf(stripped), nowMillis);
        }
        catch (RaplaException e)
        {
            throw new IllegalStateException("Failed to load stored artifacts", e);
        }
    }

    private Optional<StoredArtifact> readThrough(String id)
    {
        try
        {
            return operator.getStoredArtifacts().stream().filter(a -> a.getId().equals(id)).findFirst();
        }
        catch (RaplaException e)
        {
            throw new IllegalStateException("Failed to load stored artifact " + id, e);
        }
    }
}
