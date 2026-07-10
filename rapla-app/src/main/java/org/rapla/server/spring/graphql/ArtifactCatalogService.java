package org.rapla.server.spring.graphql;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
 * <p>Reads are pull-based (redesigned 2026-07-09): {@link #find} is a point read by natural key,
 * cached per entry for 10 s when the body is ≤ 1 MiB (larger bodies are read-through on every
 * use — the PRD 097 serving endpoint adds streaming + ETag for those); {@link #list} returns
 * metadata-only entries (bodies never travel for listings) from a demand-loaded 10 s snapshot.
 * Both caches are invalidated on every local write (same-pod read-your-own-writes; other pods
 * are at most one TTL stale — the same bound the update-history poll gives every other entity).
 * Large bodies therefore never leave the database unless an actual use requests them.
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

    private final ConcurrentMap<String, CachedEntry> entryCache = new ConcurrentHashMap<>();
    private volatile MetadataSnapshot metadataSnapshot;

    private record CachedEntry(StoredArtifact artifact, long loadedAtMillis) { }

    private record MetadataSnapshot(List<StoredArtifact> artifacts, long loadedAtMillis) { }

    public ArtifactCatalogService(StorageOperator operator, RaplaArtifactProperties properties)
    {
        this.operator = (CachableStorageOperator) operator;
        this.properties = properties;
    }

    /** All application-scoped artifacts of the given kind, metadata-only (body is always null). */
    public List<StoredArtifact> list(String kind)
    {
        final List<StoredArtifact> result = new ArrayList<>();
        for (StoredArtifact artifact : metadataSnapshot().artifacts())
        {
            if (kind.equals(artifact.getKind()))
            {
                result.add(artifact);
            }
        }
        return result;
    }

    /** Point read by natural key; the returned artifact always carries its full body. */
    public Optional<StoredArtifact> find(String kind, String name)
    {
        final String id = StoredArtifact.createId(kind, name);
        final CachedEntry cached = entryCache.get(id);
        final long nowMillis = System.currentTimeMillis();
        if (cached != null && nowMillis - cached.loadedAtMillis() < CACHE_TTL_MILLIS)
        {
            return Optional.of(cached.artifact());
        }
        final StoredArtifact artifact;
        try
        {
            artifact = operator.getStoredArtifact(id);
        }
        catch (RaplaException e)
        {
            throw new IllegalStateException("Failed to load stored artifact " + id, e);
        }
        if (artifact == null || artifact.getOwnerRef() != null)
        {
            entryCache.remove(id);   // application catalog never surfaces owned rows (D7)
            return Optional.empty();
        }
        final String body = artifact.getBody();
        if (body == null || body.getBytes(StandardCharsets.UTF_8).length <= CACHE_BODY_LIMIT_BYTES)
        {
            entryCache.put(id, new CachedEntry(artifact, nowMillis));
        }
        return Optional.of(artifact);
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
        invalidate(artifact.getId());
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
        invalidate(id);
        return true;
    }

    /** The single authorization seam for artifact writes (PRD 098 D6). Public so consumers that
     *  validate before storing (e.g. the document catalog) gate the caller first. */
    public void checkWrite(User caller) throws RaplaSecurityException
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

    private void invalidate(String id)
    {
        entryCache.remove(id);
        metadataSnapshot = null;
    }

    private MetadataSnapshot metadataSnapshot()
    {
        MetadataSnapshot snap = metadataSnapshot;
        final long nowMillis = System.currentTimeMillis();
        if (snap != null && nowMillis - snap.loadedAtMillis() < CACHE_TTL_MILLIS)
        {
            return snap;
        }
        snap = loadMetadata(nowMillis);
        metadataSnapshot = snap;
        return snap;
    }

    private MetadataSnapshot loadMetadata(long nowMillis)
    {
        try
        {
            final List<StoredArtifact> applicationScoped = new ArrayList<>();
            for (StoredArtifact artifact : operator.getStoredArtifactsMetadata())
            {
                if (artifact.getOwnerRef() != null)
                {
                    continue;   // application catalog never surfaces owned rows (D7)
                }
                if (artifact.getBody() != null)
                {
                    // file backend returns live full-bodied instances — strip a clone, never the original
                    final StoredArtifactImpl stripped = ((StoredArtifactImpl) artifact).clone();
                    stripped.setBody(null);
                    applicationScoped.add(stripped);
                }
                else
                {
                    applicationScoped.add(artifact);
                }
            }
            return new MetadataSnapshot(List.copyOf(applicationScoped), nowMillis);
        }
        catch (RaplaException e)
        {
            throw new IllegalStateException("Failed to load stored artifact metadata", e);
        }
    }
}
