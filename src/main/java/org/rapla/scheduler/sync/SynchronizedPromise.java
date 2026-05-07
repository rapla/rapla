package org.rapla.scheduler.sync;

import io.reactivex.rxjava3.functions.Action;
import io.reactivex.rxjava3.functions.BiConsumer;
import io.reactivex.rxjava3.functions.BiFunction;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.functions.Function;
import org.rapla.scheduler.Promise;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

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
        final java.util.function.Function<? super T, ? extends U> fun = (t) ->
        {
            try
            {
                return fn.apply(t);
            }
            catch (CompletionException e)
            {
                throw e;
            }
            catch (Throwable e)
            {
                throw new CompletionException(e);
            }
        };
        return w(f.thenApplyAsync(fun, promiseExecutor));
    }

    @Override
    public Promise<Void> thenAccept(Consumer<? super T> fn)
    {
        final java.util.function.Consumer<T> consumer = (a) ->
        {
            try
            {
                fn.accept(a);
            }
            catch (CompletionException e)
            {
                throw e;
            }
            catch (Throwable e)
            {
                throw new CompletionException(e);
            }
        };
        return w(f.thenAcceptAsync(consumer, promiseExecutor));
    }

    @Override
    public Promise<Void> thenRun(Action command)
    {
        final Runnable action = wrapAction(command);
        return w(f.thenRunAsync(action, promiseExecutor));
    }

    @Override
    public <U, V> Promise<V> thenCombine(Promise<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn)
    {
        final java.util.function.BiFunction<? super T, ? super U, ? extends V> bifn = (t, u) ->
        {
            try
            {
                return fn.apply(t, u);
            }
            catch (CompletionException e)
            {
                throw e;
            }
            catch (Throwable e)
            {
                throw new CompletionException(e);
            }
        };
        final CompletionStage<? extends U> v = v(other);
        return w(f.thenCombineAsync(v, bifn, promiseExecutor));
    }

    @Override
    public <U> Promise<Void> thenAcceptBoth(Promise<? extends U> other, BiConsumer<? super T, ? super U> fn)
    {
        final java.util.function.BiConsumer<? super T, ? super U> biConsumer = (t, u) ->
        {
            try
            {
                fn.accept(t, u);
            }
            catch (CompletionException e)
            {
                throw e;
            }
            catch (Throwable e)
            {
                throw new CompletionException(e);
            }
        };
        final CompletionStage<? extends U> v = v(other);
        return w(f.thenAcceptBothAsync(v, biConsumer, promiseExecutor));
    }

    private Runnable wrapAction(Action command)
    {
        return () ->
        {
            try
            {
                command.run();
            }
            catch (CompletionException e)
            {
                throw e;
            }
            catch (Throwable e)
            {
                throw new CompletionException(e);
            }
        };
    }

    @Override
    public <U> Promise<U> thenCompose(Function<? super T, ? extends Promise<U>> fn)
    {
        final java.util.function.Function<? super T, ? extends CompletionStage<U>> fun = (t) ->
        {
            try
            {
                return v(fn.apply(t));
            }
            catch (CompletionException e)
            {
                throw e;
            }
            catch (Throwable e)
            {
                throw new CompletionException(e);
            }
        };
        return w(f.thenComposeAsync(fun, promiseExecutor));
    }

    @Override
    public Promise<Void> exceptionally(Consumer<Throwable> fn)
    {
        final java.util.function.Function<Throwable, ? extends T> fun = (t) ->
        {
            try
            {
                t = getCause(t);
                fn.accept(t);
                return null;
            }
            catch (CompletionException e)
            {
                throw e;
            }
            catch (Throwable e)
            {
                throw new CompletionException(e);
            }
        };
        return w(f.exceptionally(fun).thenApply( (dummy)->null));
    }

    private Throwable getCause(Throwable t)
    {
        while (t instanceof CompletionException )
        {
            t = t.getCause();
        }
        return t;
    }

    @Override
    public  Promise<T> handle(BiFunction<? super T, Throwable, ? super T> fn) {
        final java.util.function.BiFunction<? super T, Throwable, ? super T> bifn = (t, u) ->
        {
            try
            {
                u = getCause(u);
                final T apply = (T) fn.apply(t, u);
                return apply;
            }
            catch (CompletionException e)
            {
                throw e;
            }
            catch (Throwable e)
            {
                throw new CompletionException(e);
            }
        };
        return w(f.handleAsync(bifn, promiseExecutor));
    }

    @Override
    public Promise<Void> finally_(Action fn) {
        final java.util.function.BiConsumer<? super T, ? super Throwable> bifn = (t, u) ->
        {
            try
            {
                fn.run();
            }
            catch (CompletionException e)
            {
                throw e;
            }
            catch (Throwable e)
            {
                throw new CompletionException(e);
            }
        };
        return w(f.whenCompleteAsync(bifn, promiseExecutor));
    }


    public CompletableFuture<T> toFuture()
    {
        return this.f.toCompletableFuture();
    }

    @Override
    public Promise<T> execOn(Executor executor)
    {
        return new SynchronizedPromise<T>(executor,f);
    }
}
