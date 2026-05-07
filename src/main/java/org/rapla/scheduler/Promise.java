package org.rapla.scheduler;

import io.reactivex.rxjava3.functions.Action;
import io.reactivex.rxjava3.functions.BiConsumer;
import io.reactivex.rxjava3.functions.BiFunction;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.functions.Function;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/** same as {@link java.util.concurrent.CompletionStage} but usable in gwt */
public interface Promise<T>
{
    Void VOID = null;

    /** same as {@link java.util.concurrent.CompletionStage#thenApply(java.util.function.Function)}  but usable in gwt */
    <U> Promise<U> thenApply(Function<? super T, ? extends U> fn);

    /** same as {@link java.util.concurrent.CompletionStage#thenAccept(java.util.function.Consumer)} but usable in gwt */
    Promise<Void> thenAccept(Consumer<? super T> fn);

    /** same as {@link java.util.concurrent.CompletionStage#thenRun(Runnable)}  but usable in gwt */
    Promise<Void> thenRun(Action fn);

    /** same as {@link java.util.concurrent.CompletionStage#thenCombine(CompletionStage, java.util.function.BiFunction)} but usable in gwt */
    <U, V> Promise<V> thenCombine(Promise<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn);

    /** same as {@link java.util.concurrent.CompletionStage#thenAcceptBoth(CompletionStage, java.util.function.BiConsumer)}   but usable in gwt */
    <U> Promise<Void> thenAcceptBoth(Promise<? extends U> other, BiConsumer<? super T, ? super U> fn);

    /** same as {@link java.util.concurrent.CompletionStage#thenCompose(java.util.function.Function)}       but usable in gwt */
    <U> Promise<U> thenCompose(Function<? super T, ? extends Promise<U>> fn);

    /** same as {@link java.util.concurrent.CompletionStage#exceptionally(java.util.function.Function)}        but usable in gwt */
    Promise<Void> exceptionally(Consumer<Throwable> fn);

    Promise<Void> finally_(Action run);

    /** same as {@link java.util.concurrent.CompletionStage#handle(java.util.function.BiFunction)}          but usable in gwt */
    Promise<T> handle(BiFunction<? super T, Throwable, ? super T> fn);

    Promise<T> execOn(Executor executor);
}

