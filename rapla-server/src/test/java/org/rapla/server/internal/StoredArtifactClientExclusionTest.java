package org.rapla.server.internal;

import org.junit.jupiter.api.Test;
import org.rapla.entities.storage.StoredArtifact;
import org.rapla.entities.storage.internal.ExternalSyncEntityImpl;
import org.rapla.entities.storage.internal.StoredArtifactImpl;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * PRD 098 D3 — StoredArtifact is server-only: the client-push filter must never
 * transfer it, exactly like ExternalSyncEntity. Locks the exclusion in
 * {@link UpdateDataManagerImpl#isTransferedToClient(org.rapla.entities.RaplaObject)}.
 */
public class StoredArtifactClientExclusionTest
{
    @Test
    public void storedArtifactIsNeverTransferedToClient()
    {
        StoredArtifactImpl artifact = new StoredArtifactImpl(StoredArtifact.KIND_VIEW, "leihschein");
        assertFalse(UpdateDataManagerImpl.isTransferedToClient(artifact));
        assertFalse(UpdateDataManagerImpl.isTransferedToClient(artifact, true));
    }

    @Test
    public void externalSyncEntityStaysExcludedToo()
    {
        assertFalse(UpdateDataManagerImpl.isTransferedToClient(new ExternalSyncEntityImpl()));
    }
}
