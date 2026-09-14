package org.rapla.scheduler;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/** Stage-of-computation contract; equivalent to {@link CompletionStage}.
 *  Method parameters use checked-exception-allowing function types from this package
 *  ({@link Function}, {@link Consumer}, etc.) so callers can throw checked exceptions
 *  from inside lambda bodies without wrapping. The impls bridge to {@link CompletionStage}
 *  internally. */
public interface Promise<T>
{
    Void VOID = null;

    <U> Promise<U> thenApply(Function<? super T, ? extends U> fn);

    Promise<Void> thenAccept(Consumer<? super T> fn);

    Promise<Void> thenRun(Action fn);

    <U, V> Promise<V> thenCombine(Promise<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn);

    <U> Promise<Void> thenAcceptBoth(Promise<? extends U> other, BiConsumer<? super T, ? super U> fn);

    <U> Promise<U> thenCompose(Function<? super T, ? extends Promise<U>> fn);

    Promise<Void> exceptionally(Consumer<Throwable> fn);

    Promise<Void> finally_(Action run);

    Promise<T> handle(BiFunction<? super T, Throwable, ? super T> fn);

    Promise<T> execOn(Executor executor);
}
