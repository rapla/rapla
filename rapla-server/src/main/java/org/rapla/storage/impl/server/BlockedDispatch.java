package org.rapla.storage.impl.server;

import java.util.List;
import java.util.function.LongSupplier;

import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.framework.RaplaException;
import org.rapla.storage.StorageOperator;

/**
 * Writes a large change set in blocks, yielding between them so it cannot hold the store locks
 * for its whole duration.
 *
 * <p>The pattern predates this class in two places — {@code ArchiverServiceImpl} (blocks of 100,
 * no yield) and dhbwrapla's {@code AbstractRaplaMapping.commitTransaction} (blocks of 100 plus a
 * pause of a third of the block's duration, explicitly "to allow other threads to get a lock").
 * Both keep their own copy for now; new callers use this one rather than adding a fourth.
 *
 * <p>A blocked dispatch is NOT one transaction. Only use it where the caller is idempotent, so
 * that dying between blocks leaves work the next run redoes rather than a broken state.
 */
public final class BlockedDispatch
{
    public static final int DEFAULT_BLOCK_SIZE = 100;

    private final StorageOperator operator;
    private final int blockSize;
    private final LongSupplier clock;

    public BlockedDispatch(StorageOperator operator, int blockSize, LongSupplier clock)
    {
        this.operator = operator;
        this.blockSize = blockSize;
        this.clock = clock;
    }

    public <T extends Entity, S extends Entity> void storeAndRemove(List<T> toStore,
            List<ReferenceInfo<S>> toRemove, User user) throws RaplaException
    {
        for (int from = 0; from < toRemove.size(); from += blockSize)
        {
            final List<ReferenceInfo<S>> block = toRemove.subList(from, Math.min(from + blockSize, toRemove.size()));
            yielding(() -> operator.storeAndRemove(List.of(), block, user));
        }
        for (int from = 0; from < toStore.size(); from += blockSize)
        {
            final List<T> block = toStore.subList(from, Math.min(from + blockSize, toStore.size()));
            yielding(() -> operator.storeAndRemove(block, List.of(), user));
        }
    }

    private void yielding(Dispatch dispatch) throws RaplaException
    {
        final long start = clock.getAsLong();
        dispatch.run();
        final long duration = clock.getAsLong() - start;
        if (duration <= 0)
        {
            return;
        }
        try
        {
            Thread.sleep(duration / 3);
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface Dispatch
    {
        void run() throws RaplaException;
    }
}
