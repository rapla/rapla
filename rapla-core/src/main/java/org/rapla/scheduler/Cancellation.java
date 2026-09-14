package org.rapla.scheduler;

/** Handle for cancelling a scheduled or in-flight task. Closeable-shaped but no-throw. */
@FunctionalInterface
public interface Cancellation
{
    void cancel();
}
