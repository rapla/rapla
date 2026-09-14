package org.rapla.framework;

/**
 * PRD 056 §9 checkIdIntegrity check #1 — a declared CREATE whose id already
 * resolves to a persistent entity. Per the revised OQ5 there is no content
 * comparison: clients own their id space and map a collision on a
 * self-generated id to "already applied" (retry idempotency). §12: the
 * message names only the client's own id, never the colliding entity.
 */
public class EntityIdCollisionException extends RaplaException
{
    private static final long serialVersionUID = 1L;

    public EntityIdCollisionException(String id)
    {
        super("Id " + id + " already exists — cannot create");
    }
}
