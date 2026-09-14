package org.rapla.scheduler;

import java.util.concurrent.Executor;

public interface CommandScheduler
{
    /** Underlying worker executor — used by client-side {@code Observables} factories to attach
     *  rxjava streams to the same thread pool that promises run on. */
    Executor getExecutor();

    /** if two commands are scheduled for the same synchronisation object then they must be executed in the order in which they are scheduled*/
    Promise<Void> scheduleSynchronized(final Object synchronizationObject, Action task);
    Promise<Void> run(Action task);
    <T> Promise<T> supply(Callable<T> supplier);
    <T> CompletablePromise<T> createCompletable();

    @FunctionalInterface
    interface Callable<T> {
        T call() throws Exception;
    }

    /** Run task once after the given delay. Returns a handle that cancels the pending task. */
    Cancellation delay(Action task, long milliseconds);

    /** Run task periodically at the given fixed rate after the initial delay. */
    Cancellation schedule(Action task, long initialDelayMillisesconds, long delayBetweenExecutionsMilliseconds);
}
