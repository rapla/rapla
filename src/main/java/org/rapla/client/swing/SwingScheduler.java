package org.rapla.client.swing;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.rapla.framework.internal.DefaultScheduler;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.scheduler.Cancelable;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.CompletablePromise;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.UnsynchronizedCompletablePromise;

@DefaultImplementation(of = CommandScheduler.class,context = InjectionContext.swing)
@Singleton
public class SwingScheduler extends DefaultScheduler
{
    Logger logger;
    private boolean waitingInCommandThread = false;
    @Inject
    public SwingScheduler(Logger logger)
    {
        super(logger, 3);
        this.logger = logger;
    }

    @Override
    public <T> Promise<T> synchronizeTo(Promise<T> promise)
    {
    	final int index = getInt();
    	logger.debug("Invoking update "+ index + " trace " + Arrays.asList(Thread.currentThread().getStackTrace()));
        AtomicLong longTest = new AtomicLong();
        final CompletablePromise<T> completablePromise = new UnsynchronizedCompletablePromise<>();
        promise.whenComplete((t, ex) ->
        {
        	logger.debug("SwingUtilities invoke later " + index + " background promise complete.");
            Runnable runnable= () ->
            {
            	{
            		long timeForRunnable =System.currentTimeMillis() - longTest.get();
            		logger.debug("SwingUtilities invoke later complete  " + index + " took " + timeForRunnable + " ms");
            	}
                if (ex != null)
                {
                    completablePromise.completeExceptionally(ex);
                }
                else
                {
                    completablePromise.complete(t);
                }	
                {
            		long timeForRunnable =System.currentTimeMillis() - longTest.get();
            		logger.debug("SwingUtilities invoke later complete notify  " + index + " took " + timeForRunnable + " ms");
            	}
            };
            longTest.set(System.currentTimeMillis());
            if (javax.swing.SwingUtilities.isEventDispatchThread())
            {
            	runnable.run();
            }
            if (!waitingInCommandThread )
            {
            	javax.swing.SwingUtilities.invokeLater(runnable);
            }
            else
            {
            	schedule(runnable,0);
            }
        });
        return completablePromise;
    }
    
    public <T> T waitFor(Promise<T> promise, int timeout) throws Throwable
    {
    	 final int index = getInt();
         
		logger.debug("Aquiere lock " + index);
	
		boolean aquireCommand = false;
    	if (javax.swing.SwingUtilities.isEventDispatchThread() )
    	{
    		aquireCommand = true;
    		waitingInCommandThread = true;
    	}
    	try
    	{
    		final CompletableFuture<T> future = new CompletableFuture<>();
            AtomicLong longTest = new AtomicLong();
            longTest.set(System.currentTimeMillis());
            promise.whenComplete((t, ex) -> {
            	 logger.debug("promise complete " +index);
            	if ( ex != null)
            	{
            		future.completeExceptionally( ex);
            	}
            	else
            	{
            		future.complete( t );
            	}
                logger.debug("Release lock  " +index);
            });
            try
            {
        		long timeForRunnable =System.currentTimeMillis() - longTest.get();
            	T t = future.get(timeout, TimeUnit.MILLISECONDS);
            	logger.debug("SwingUtilities waitFor "+ index + " took " + timeForRunnable + " ms");
            	return t;
            	
            }
            catch (ExecutionException ex)
            {
            	throw ex.getCause();
            }
    	}
    	finally
    	{
    		if (aquireCommand)
    		{
    			waitingInCommandThread = false;
    		}
    	}
        
    }

	private int getInt() {
		IntStream ints = new java.util.Random().ints();
    	 final int index = ints.iterator().nextInt();
		return index;
	}
    

    @Override
    public Cancelable scheduleSynchronized(Object synchronizationObject, Runnable task, long delay)
    {
        Runnable swingTask = new Runnable()
        {
            @Override
            public void run()
            {
                javax.swing.SwingUtilities.invokeLater(task);
            }
        };
        return super.scheduleSynchronized(synchronizationObject, swingTask, delay);
    }
}
