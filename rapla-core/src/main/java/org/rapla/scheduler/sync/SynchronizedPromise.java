package org.rapla.scheduler.sync;

import org.rapla.scheduler.Promise;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class SynchronizedPromise<T> implements Promise<T>
{

    final Executor promiseExecutor;
    final CompletionStage f;

    SynchronizedPromise(Executor executor, CompletionStage f)
    {
        this.promiseExecutor = executor;
        this.f = f;
    }

    protected <U> Promise<U> w(CompletionStage<U> stage)
    {
        return new SynchronizedPromise<U>(promiseExecutor, stage);
    }

    protected <T> CompletionStage<T> v(final Promise<T> promise)
    {
        if (promise instanceof SynchronizedPromise)
        {
            return ((SynchronizedPromise) promise).f;
        }
        else
        {
            CompletableFuture<T> future = new CompletableFuture<T>();
            promise.thenAccept((t) -> future.complete(t)).exceptionally((ex) ->
            {
                future.completeExceptionally(ex);
            });
            return future;
        }
    }

    public CompletionStage getCompletionStage() {
        return f;
    }

    @Override
    public <U> Promise<U> thenApply(Function<? super T, ? extends U> fn)
    {
        return w(f.thenApplyAsync(fn, promiseExecutor));
    }

    @Override
    public Promise<Void> thenAccept(Consumer<? super T> fn)
    {
        return w(f.thenAcceptAsync(fn, promiseExecutor));
    }

    @Override
    public Promise<Void> thenRun(Runnable command)
    {
        return w(f.thenRunAsync(command, promiseExecutor));
    }

    @Override
    public <U, V> Promise<V> thenCombine(Promise<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn)
    {
        final CompletionStage<? extends U> v = v(other);
        return w(f.thenCombineAsync(v, fn, promiseExecutor));
    }

    @Override
    public <U> Promise<Void> thenAcceptBoth(Promise<? extends U> other, BiConsumer<? super T, ? super U> fn)
    {
        final CompletionStage<? extends U> v = v(other);
        return w(f.thenAcceptBothAsync(v, fn, promiseExecutor));
    }

    @Override
    public <U> Promise<U> thenCompose(Function<? super T, ? extends Promise<U>> fn)
    {
        final Function<? super T, ? extends CompletionStage<U>> fun = (t) -> v(fn.apply(t));
        return w(f.thenComposeAsync(fun, promiseExecutor));
    }

    @Override
    public Promise<Void> exceptionally(Consumer<Throwable> fn)
    {
        final Function<Throwable, ? extends T> fun = (t) ->
        {
            fn.accept(getCause(t));
            return null;
        };
        return w(f.exceptionally(fun).thenApply((dummy) -> null));
    }

    private Throwable getCause(Throwable t)
    {
        while (t instanceof CompletionException)
        {
            t = t.getCause();
        }
        return t;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Promise<T> handle(BiFunction<? super T, Throwable, ? super T> fn)
    {
        // CompletionStage.handleAsync needs ? extends T on the output; Promise.handle declares ? super T.
        // The caller is documented as recovering to T (or compatible), so cast at the boundary.
        final BiFunction<? super T, Throwable, T> bifn = (t, u) -> (T) fn.apply(t, getCause(u));
        return w(f.handleAsync(bifn, promiseExecutor));
    }

    @Override
    public Promise<Void> finally_(Runnable fn)
    {
        final BiConsumer<? super T, ? super Throwable> bifn = (t, u) -> fn.run();
        return w(f.whenCompleteAsync(bifn, promiseExecutor));
    }


    public CompletableFuture<T> toFuture()
    {
        return this.f.toCompletableFuture();
    }

    @Override
    public Promise<T> execOn(Executor executor)
    {
        return new SynchronizedPromise<T>(executor, f);
    }
}
