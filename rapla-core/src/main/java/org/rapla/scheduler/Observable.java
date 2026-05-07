package org.rapla.scheduler;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.ObservableSource;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.core.SingleSource;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Action;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.functions.Function;
import org.reactivestreams.Publisher;

import java.util.concurrent.TimeUnit;

public interface Observable<T> extends Publisher<T> {
    /**
     * @see Flowable#subscribe()
     */
    Disposable subscribe();
    /**
     * @see Flowable#subscribe(Consumer)
     */
    Disposable subscribe(Consumer<? super T> consumer);
    /**
     * @see Flowable#throttleLast(long, TimeUnit)
     */
    Observable<T> throttle(long milliseconds);
    /**
     * @see Flowable#delay(long, TimeUnit) ()
     */
    Observable<T> delay(long milliseconds);
    /**
     * @see Flowable#repeat()
     */
    Observable<T> repeat();

    /**
     * @see Flowable#share()
     */
    Observable<T> share();
    /**
     * @see Flowable#concatWith(Publisher)
     */
    Observable<T> concatWith(Observable<? extends T> otherObservable);

    /**
     * @see Flowable#doOnError(Consumer)
     */
    Observable<T> doOnError(Consumer<? super Throwable> error);

    /**
     * @see Flowable#onErrorResumeNext(Function)
     */
    Observable<T> onErrorResumeNext(Consumer<? super Throwable> onError);

    /**
     * @see Flowable#doOnComplete(Action)
     */
    Observable<T> doOnComplete(Action action);

    /**
     * @see Flowable#doOnNext(Consumer)
     */
    Observable<T> doOnNext(Consumer<? super T> next);
    /**
     * @see Flowable#map(Function)
     */
    <R> Observable<R> map(Function<? super T, ? extends R> mapper);
    /**
     * @see Flowable#flatMap(Function)
     */
    <R> Observable<R> flatMap(Function<? super T, ? extends Publisher<? extends R>> mapper);
    /**
     * @see Flowable#switchMap(Function)
     */
    <R> Observable<R> switchMap(Function<? super T, ? extends Publisher<? extends R>> mapper);
    /**
     * @see Flowable#debounce(long, TimeUnit)
     */
    Observable<T> debounce(long time);
    Object toNativeObservable();
}
