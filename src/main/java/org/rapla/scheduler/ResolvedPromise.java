package org.rapla.scheduler;

public class ResolvedPromise<T> extends UnsynchronizedPromise<T>
{
    final public static Promise<Void> VOID_PROMISE = new ResolvedPromise<Void>((Void) null);

    public ResolvedPromise(T result)
    {
        super(result);
    }

    public ResolvedPromise(Throwable ex)
    {
        super(ex);
    }

    public T getResult()
    {
        return result;
    }

    public Throwable getException()
    {
        return exception;
    }

    public boolean isCompleted()
    {
        return result != null;
    }



}
