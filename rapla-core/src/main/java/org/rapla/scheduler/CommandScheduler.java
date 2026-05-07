package org.rapla.scheduler;

import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Action;

import java.util.concurrent.TimeUnit;

public interface CommandScheduler
{
    //Cancelable schedule(Action command, long delay, long period);
    /** if two commands are scheduled for the same synchronisation object then they must be executed in the order in which they are scheduled*/
    Promise<Void> scheduleSynchronized(final Object synchronizationObject, Action task);
    <T> Observable<T> just(T t);
    Promise<Void> run(Action task);
    <T> Promise<T> supply(Callable<T> supplier);
    <T> Subject<T> createPublisher();
    <T> CompletablePromise<T> createCompletable();
    <T> Observable<T> toObservable(Promise<T> promise);

    @FunctionalInterface
    interface Callable<T> {
        T call() throws Exception;
    }

    default Disposable delay( Action task, long milliseconds)
    {
        final Observable<Integer> delay = just(01).delay(milliseconds);
        final Disposable subscribe = delay.subscribe((dummy) -> task.run());
        return subscribe;
    }

    default Disposable schedule(Action task, long initialDelayMillisesconds, long delayBetweenExecutionsMilliseconds)
    {
        final Observable<Long> initialDelay = just(0l).delay(initialDelayMillisesconds);
        final Observable<Long> repeat = just(0l).flatMap((dummy) -> just(0l).map((t) ->
        {
            task.run();
            return 0l;
        })).delay(delayBetweenExecutionsMilliseconds).repeat();
        Disposable subscribe = initialDelay.concatWith(repeat).subscribe();
        return subscribe;
    }

}
