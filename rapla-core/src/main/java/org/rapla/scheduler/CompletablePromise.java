package org.rapla.scheduler;


public interface CompletablePromise<T> extends Promise<T>
{
    void complete(T value);
    void completeExceptionally(Throwable ex);
    boolean isDone();
}
