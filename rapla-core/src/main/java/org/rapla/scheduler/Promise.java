package org.rapla.scheduler;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/** Stage-of-computation contract; equivalent to {@link CompletionStage}.
 *  Historically wrapped {@link CompletionStage} so the codebase could share async chains with GWT;
 *  GWT is gone, the wrapper survives only because it carries a sticky {@link #execOn} executor. */
public interface Promise<T>
{
    Void VOID = null;

    <U> Promise<U> thenApply(Function<? super T, ? extends U> fn);

    Promise<Void> thenAccept(Consumer<? super T> fn);

    Promise<Void> thenRun(Runnable fn);

    <U, V> Promise<V> thenCombine(Promise<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn);

    <U> Promise<Void> thenAcceptBoth(Promise<? extends U> other, BiConsumer<? super T, ? super U> fn);

    <U> Promise<U> thenCompose(Function<? super T, ? extends Promise<U>> fn);

    Promise<Void> exceptionally(Consumer<Throwable> fn);

    Promise<Void> finally_(Runnable run);

    Promise<T> handle(BiFunction<? super T, Throwable, ? super T> fn);

    Promise<T> execOn(Executor executor);
}
