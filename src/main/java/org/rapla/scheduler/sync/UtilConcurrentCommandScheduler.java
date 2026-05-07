package org.rapla.scheduler.sync;

import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Action;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import org.rapla.logger.Logger;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.CompletablePromise;
import org.rapla.scheduler.Observable;
import org.rapla.scheduler.Promise;

import java.time.Clock;
import java.time.LocalTime;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalField;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.HOURS;

public class UtilConcurrentCommandScheduler implements CommandScheduler, Executor
{
    private final ScheduledExecutorService scheduledExecutor;
    private final Executor promiseExecuter;
    protected final Logger logger;

    private ConcurrentHashMap<Object, CancableTask> futureTasks = new ConcurrentHashMap<Object, CancableTask>();

    public UtilConcurrentCommandScheduler(Logger logger)
    {
        this(logger, 6);
    }

    public UtilConcurrentCommandScheduler(Logger logger, int poolSize)
    {
        this.logger = logger;
        final ScheduledExecutorService executor = Executors.newScheduledThreadPool(poolSize, new ThreadFactory()
        {

            public Thread newThread(Runnable r)
            {
                Thread thread = new Thread(r);
                String name = thread.getName();
                if (name == null)
                {
                    name = "";
                }
                thread.setName("raplascheduler-" + name.toLowerCase().replaceAll("thread", "").replaceAll("-|\\[|\\]", ""));
                thread.setDaemon(true);
                return thread;
            }
        });

        this.scheduledExecutor = executor;
        this.promiseExecuter = executor;
    }

    public void execute(Runnable task)
    {
        scheduledExecutor.execute(task);
    }

    protected void schedule(Runnable task)
    {
        if (scheduledExecutor.isShutdown())
        {
            Exception ex = new Exception("Can't schedule command because executer is already shutdown " + task.toString());
            error(ex.getMessage(), ex);
            return;
        }

        TimeUnit unit = TimeUnit.MILLISECONDS;
        long delay = 0;
        ScheduledFuture<?> schedule = scheduledExecutor.schedule(task, delay, unit);
    }

    protected void error(String message, Exception ex)
    {
        logger.error(message, ex);
    }

    protected void debug(String message)
    {
        logger.debug(message);
    }

    protected void info(String message)
    {
        logger.info(message);
    }

    protected void warn(String message)
    {
        logger.warn(message);
    }

    @Override
    public  Promise<Void> scheduleSynchronized(Object synchronizationObject, Action task)
    {
        final CompletablePromise<Void> completable = createCompletable();
        scheduleSynchronized(synchronizationObject, task, completable);
        return completable;
    }

    protected void scheduleSynchronized(Object synchronizationObject, Action task, CompletablePromise<Void> completable) {
        CancableTask wrapper = new CancableTask(task, completable)
        {
            @Override
            protected void replaceWithNext(CancableTask next)
            {
                futureTasks.replace(synchronizationObject, this, next);
            }

            @Override
            protected void endOfQueueReached()
            {
                synchronized (synchronizationObject)
                {
                    futureTasks.remove(synchronizationObject);
                }
            }
        };
        synchronized (synchronizationObject)
        {
            CancableTask existing = futureTasks.putIfAbsent(synchronizationObject, wrapper);
            if (existing == null)
            {
                wrapper.scheduleThis();
            }
            else
            {
                existing.pushToEndOfQueue(wrapper);
            }
        }
    }

    protected void execute(Action action ,CompletablePromise<Void> completablePromise) {
        try
        {
            action.run();
        }
        catch (Throwable ex )
        {
            completablePromise.completeExceptionally( ex );
            return;
        }
        completablePromise.complete( null );
    }


    abstract class CancableTask implements  Runnable
    {
        private Action task;

        volatile Thread.State status = Thread.State.NEW;
        CancableTask next;
        CompletablePromise<Void> completablePromise;

        public CancableTask(Action task,CompletablePromise<Void> completablePromise)
        {
            this.task = task;
            this.completablePromise = completablePromise;
        }

        @Override
        public void run()
        {
            if (status == Thread.State.NEW) {
                status = Thread.State.RUNNABLE;
                try {
                    execute(task,completablePromise);
                }
                finally
                {
                    status = Thread.State.TERMINATED;
                    scheduleNext();
                }
            }
            
            
        }




        public void pushToEndOfQueue(CancableTask wrapper)
        {
            if (next == null)
            {
                next = wrapper;
            }
            else
            {
                next.pushToEndOfQueue(wrapper);
            }
        }

        public void scheduleThis()
        {
            schedule(this);
        }

        private void scheduleNext()
        {
            if (next != null)
            {
                replaceWithNext(next);
                next.scheduleThis();
            }
            else
            {
                endOfQueueReached();
            }
        }

        abstract protected void replaceWithNext(CancableTask next);

        abstract protected void endOfQueueReached();
    }


    /*
    public  <T> T waitFor(Promise<T> promise, int timeout) throws Throwable
    {
        Semaphore semaphore = new Semaphore(0);
        AtomicReference<T> atomicReference = new AtomicReference<>();
        AtomicReference<Throwable> atomicReferenceE = new AtomicReference<>();
        promise.whenComplete((t, ex) -> {
            atomicReferenceE.set(ex);
            atomicReference.set(t);
            semaphore.release();
        });
        semaphore.tryAcquire(timeout, TimeUnit.MILLISECONDS);
        final Throwable throwable = atomicReferenceE.get();
        if (throwable != null)
        {
            throw throwable;
        }
        final T t = atomicReference.get();
        return t;
    }
*/


    public void cancel()
    {
        try
        {
            info("Stopping scheduler thread.");
            List<Runnable> shutdownNow = scheduledExecutor.shutdownNow();
            for (Runnable task : shutdownNow)
            {
                long delay = -1;
                if (task instanceof ScheduledFuture)
                {
                    ScheduledFuture scheduledFuture = (ScheduledFuture) task;
                    delay = scheduledFuture.getDelay(TimeUnit.SECONDS);
                }
                if (delay <= 0)
                {
                    warn("Interrupted active task " + task);
                }
            }
            scheduledExecutor.awaitTermination(2, TimeUnit.SECONDS);
            info("Stopped scheduler thread.");
        }
        catch (Throwable ex)
        {
            warn(ex.getMessage());
        }
        // we give the update threads some time to execute
        try
        {
            Thread.sleep(50);
        }
        catch (InterruptedException e)
        {
        }
    }

    @Override
    public <T> Promise<T> supply(Callable<T> supplier)
    {
        return supply(supplier, promiseExecuter);
    }

//    @Override
    public Promise<Void> delay(long delay) {
        CompletablePromise<Void> promise = createCompletable();
        Runnable task = ()->promise.complete(null);
        scheduledExecutor.schedule(task, delay, TimeUnit.MILLISECONDS);
        return promise;
    }

    @Override
    public <T> Observable<T> just(T t) {
        final io.reactivex.rxjava3.core.Flowable<T> just = io.reactivex.rxjava3.core.Flowable.just(t);
        return new JavaObservable<T>(just, promiseExecuter);
    }

    private <T> Promise<T> supply(final Callable<T> supplier, Executor executor)
    {
        if (supplier == null)
            throw new NullPointerException();
        CompletableFuture<T> future = new CompletableFuture<T>();
        executor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                final T t;
                try
                {
                    t = supplier.call();
                }
                catch (Exception ex)
                {
                    future.completeExceptionally(ex);
                    return;
                }
                future.complete(t);
            }
        });
        SynchronizedPromise<T> promise = new SynchronizedPromise<T>(executor, future);
        return promise;
    }

    private Promise<Void> run(final Action command, Executor executor)
    {
        if (command == null)
            throw new NullPointerException();
        CompletableFuture<Void> future = new CompletableFuture<Void>();
        executor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    command.run();
                    future.complete(Promise.VOID);
                }
                catch (Throwable ex)
                {
                    future.completeExceptionally(ex);
                }
            }
        });
        SynchronizedPromise<Void> promise = new SynchronizedPromise<Void>(executor, future);
        return promise;
    }

    @Override
    public Promise<Void> run(Action command)
    {
        return run(command, promiseExecuter);
    }

    @Override
    public <T> CompletablePromise<T> createCompletable()
    {
        SynchronizedCompletablePromise<T> promise = new SynchronizedCompletablePromise<T>(promiseExecuter);
        return promise;
    }

    @Override
    public <T> Observable<T> toObservable(Promise<T> promise)
    {
        JavaObservable<T> javaObservable;
        if ( promise instanceof SynchronizedPromise)
        {
            SynchronizedPromise synchronizedPromise = (SynchronizedPromise) promise;
            javaObservable = new JavaObservable(synchronizedPromise, promiseExecuter);
        }
        else
        {
            final PublishProcessor<T> publishSubject = PublishProcessor.create();
            promise.handle((arg, throwable) ->
            {
                if (throwable != null)
                {
                    try
                    {
                        publishSubject.onError(throwable);
                    }
                    finally
                    {
                        publishSubject.onComplete();
                    }
                }
                else
                {
                    if ( arg != null) {
                        try {
                            publishSubject.onNext(arg);
                        } finally {
                            publishSubject.onComplete();
                        }
                    } else {
                        publishSubject.onComplete();
                    }
                }
                return arg;
            });
            javaObservable = new JavaObservable<T>(publishSubject, promiseExecuter);
        }
        return javaObservable;
    }

    @Override
    public <T> org.rapla.scheduler.Subject<T> createPublisher()
    {
        PublishProcessor<T> subject = PublishProcessor.create();
        return new JavaSubject<>(subject, promiseExecuter);
    }


//    Disposable schedule(Action task, int hourOfDay, int minute)
//    {
//        Clock clock = Clock.systemDefaultZone();
//        LocalTime time = LocalTime.of(hourOfDay,minute);
//        HOURS.between( clock.instant(), time);
//    }

    /*
    public <T> Promise<T> synchronizeTo(Promise<T> promise)
    {
        return synchronizeTo(promise,promiseExecuter);
    }
    /*

    /** the promise complete and exceptional methods will be called with the passed executer Consumer&lt;Runnable&gt; is the same as java.util.concurrent.Executor interface
     * You can use this to synchronize to SwingEventQueues*/
    /*
    public <T> Promise<T> synchronizeTo(Promise<T> promise, Executor executor)
    {
        final CompletablePromise<T> completablePromise = new UnsynchronizedCompletablePromise<>();
        promise.whenComplete((t, ex) ->
        {
            executor.execute(() ->
            {
                if (ex != null)
                {
                    completablePromise.completeExceptionally(ex);
                }
                else
                {
                    completablePromise.complete(t);
                }
            });
        });
        return completablePromise;
    }
    */

}