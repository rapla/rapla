package org.rapla.reactivex;

import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposable;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.schedulers.Schedulers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.logger.ConsoleLogger;
import org.rapla.scheduler.Observable;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.Subject;
import org.rapla.scheduler.sync.UtilConcurrentCommandScheduler;

import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class ObservableTest {
    @Test
    public void testDirect()
    {
        final PublishProcessor<Long> subject = PublishProcessor.create();
        Flowable<Long> debounce = subject.
                debounce(405, TimeUnit.MILLISECONDS);
        debounce.subscribe((l) ->System.out.println(" Clickpause fuer " + l));
        final Flowable<String> call = debounce.
                switchMap((time) -> Flowable.just("Aufruf mit: " + time).delay(1000, TimeUnit.MILLISECONDS));
        call.subscribe(System.out::println);

        System.out.println("Start pushing");
        Scheduler computation = Schedulers.computation();
        int max = 10;
        for (int i = 0; i< max; i++) {
            final long l = i;
            long delay = l * 300+ l*105;
            computation.scheduleDirect(() -> {subject.onNext(l);System.out.println(delay + "ms Pushing "+ l);}, delay,TimeUnit.MILLISECONDS);
        }
        subject.takeUntil(Flowable.timer(5500,TimeUnit.MILLISECONDS)).blockingSubscribe();
    }

    @Test
    public void testIndirect()
    {
        UtilConcurrentCommandScheduler scheduler = new UtilConcurrentCommandScheduler(new ConsoleLogger());
        final Subject<Long> subject = scheduler.createPublisher();
        Observable<Long> debounce = subject.
                debounce(405);
        debounce.subscribe((l) ->System.out.println(" Clickpause fuer " + l));
        final Observable<String> call = debounce.
                switchMap((time) -> {

                    final Promise<String> promise = scheduler.supply(() -> ("Aufruf mit: " + time));
                    final Observable<String> stringObservable = scheduler.toObservable(promise);
                    return stringObservable;
                });
        call.subscribe(System.out::println);

        System.out.println("Start pushing");
        Scheduler computation = Schedulers.computation();
        int max = 10;
        for (int i = 0; i< max; i++) {
            final long l = i;
            long delay = l * 300+ l*105;
            computation.scheduleDirect(() -> {subject.onNext(l);System.out.println(delay + "ms Pushing "+ l);}, delay,TimeUnit.MILLISECONDS);
        }
        ((PublishProcessor)subject.toNativeObservable()).takeUntil(Flowable.timer(5500,TimeUnit.MILLISECONDS)).blockingSubscribe();
    }

    @Test
    public void testTimer() throws Exception {
        final Disposable subscribe = Flowable
                .just("Hallo")
                .flatMap(i -> {
                    System.out.println("" + i);
                    return Flowable.just(i).delay(1000,TimeUnit.MILLISECONDS);
                }).delay(900, TimeUnit.MILLISECONDS)
                .repeat()
                .subscribe();
        Thread.sleep(5000);
        subscribe.dispose();
        Thread.sleep(2000);
    }

    @Test
    public void testTimerIntern() throws InterruptedException {
        final ConsoleLogger logger = new ConsoleLogger();
        UtilConcurrentCommandScheduler scheduler = new UtilConcurrentCommandScheduler(logger);
        long start = System.currentTimeMillis();
        final Observable<Long> repeat = scheduler.just(1l).delay(500).concatWith(scheduler.just("Scheduler").flatMap((dummy) -> scheduler.just(1).map((t)->
        {
            logger.info("Calling method at " + ( System.currentTimeMillis()-start));
            Thread.sleep(500);
            logger.info("method called");
            return 1l;
        })).delay(500).repeat());
        Disposable subscribe = repeat.subscribe();
        Thread.sleep(5000);
        logger.info("Disposing");
        subscribe.dispose();
        Thread.sleep(3000);
    }

}
