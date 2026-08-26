package org.rapla.server.spring.graphql;

import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.storage.EntityReferencer;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.framework.RaplaException;
import org.rapla.server.internal.SecurityManager;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.UpdateEvent;
import org.rapla.storage.impl.EntityStore;

/** Permission gate every GraphQL write passes before {@code operator.dispatch} — mirrors
 *  {@code RemoteStorageController.dispatch_} on the Swing wire. */
final class WriteGate
{
    private WriteGate() {}

    static void check(SecurityManager security, StorageOperator operator, UpdateEvent event) throws RaplaException
    {
        String userId = event.getUserId();
        if (userId == null) return;
        User user = operator.tryResolve(new ReferenceInfo<>(userId, User.class));
        if (user == null) return;
        EntityStore store = new EntityStore(operator);
        store.addAll(event.getStoreObjects());
        for (EntityReferencer references : event.getEntityReferences())
        {
            references.setResolver(store);
        }
        try
        {
            for (Entity entity : event.getStoreObjects())
            {
                security.checkWritePermissions(user, entity);
            }
            for (ReferenceInfo id : event.getRemoveIds())
            {
                Entity entity = operator.tryResolve(id);
                if (entity != null)
                {
                    security.checkDeletePermissions(user, entity);
                }
            }
        }
        catch (RaplaSecurityException ex)
        {
            throw new ReservationMutationController.ReservationMutationException("PERMISSION_DENIED", "input", ex.getMessage());
        }
    }
}
