package org.rapla.scheduler;

import io.reactivex.rxjava3.processors.PublishProcessor;
import org.rapla.scheduler.sync.JavaObservable;
import org.rapla.scheduler.sync.JavaSubject;
import org.rapla.scheduler.sync.SynchronizedPromise;

import java.util.concurrent.Executor;

/** Client-side factory methods for {@link Observable}/{@link Subject} that previously
 *  lived on {@link CommandScheduler}. Lives in {@code rapla-client} because it pulls in rxjava. */
public final class Observables
{
    private Observables() {}

    public static <T> Observable<T> just(T t, Executor executor)
    {
        final io.reactivex.rxjava3.core.Flowable<T> just = io.reactivex.rxjava3.core.Flowable.just(t);
        return new JavaObservable<T>(just, executor);
    }

    public static <T> Subject<T> createPublisher(Executor executor)
    {
        PublishProcessor<T> subject = PublishProcessor.create();
        return new JavaSubject<>(subject, executor);
    }

    public static <T> Observable<T> toObservable(Promise<T> promise, Executor executor)
    {
        if (promise instanceof SynchronizedPromise)
        {
            return new JavaObservable<T>((SynchronizedPromise<T>) promise, executor);
        }
        final PublishProcessor<T> publishSubject = PublishProcessor.create();
        promise.handle((arg, throwable) ->
        {
            if (throwable != null)
            {
                try { publishSubject.onError(throwable); }
                finally { publishSubject.onComplete(); }
            }
            else
            {
                if (arg != null)
                {
                    try { publishSubject.onNext(arg); }
                    finally { publishSubject.onComplete(); }
                }
                else
                {
                    publishSubject.onComplete();
                }
            }
            return arg;
        });
        return new JavaObservable<T>(publishSubject, executor);
    }
}
