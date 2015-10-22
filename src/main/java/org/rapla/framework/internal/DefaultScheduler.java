package org.rapla.framework.internal;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.rapla.components.util.Cancelable;
import org.rapla.components.util.Command;
import org.rapla.components.util.CommandScheduler;
import org.rapla.framework.Disposable;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import javax.inject.Singleton;

@DefaultImplementation(of=CommandScheduler.class,context = {InjectionContext.server})
@Singleton
public class DefaultScheduler implements CommandScheduler, Disposable
{
	private final ScheduledExecutorService executor;
	Logger logger;
	
	protected Logger getLogger() 
	{
        return logger;
    }

	@Inject
	public DefaultScheduler(Logger logger) {
	    this(logger, 6);
	}

	public DefaultScheduler(Logger logger, int poolSize) {
	    this.logger = logger;
		final ScheduledExecutorService executor = Executors.newScheduledThreadPool(poolSize,new ThreadFactory() {
			
			public Thread newThread(Runnable r) {
				Thread thread = new Thread(r);
				String name = thread.getName();
				if ( name == null)
				{
					name = "";
				}
				thread.setName("raplascheduler-" + name.toLowerCase().replaceAll("thread", "").replaceAll("-|\\[|\\]", ""));
				thread.setDaemon(true);
				return thread;
			}
		});
		this.executor = executor;
	}

	public void execute(Runnable task)
	{
	   schedule(task, 0 );
	}

	public Cancelable schedule(Command command, long delay) 
	{
		Runnable task = createTask(command);
		return schedule(task, delay);
	}
	
	protected Runnable createTask(final Command command) {
	    Runnable timerTask = new Runnable() {
	        public void run() {
	            try {
	                command.execute();
	            } catch (Exception e) {
	                getLogger().error( e.getMessage(), e);
	            }                   
	        }
	        public String toString()
	        {
	            return command.toString();
	        }
	    };
	    return timerTask;
	}

	
	public Cancelable schedule(Runnable task, long delay) {
		if (executor.isShutdown())
		{
			RaplaException ex = new RaplaException("Can't schedule command because executer is already shutdown " + task.toString());
			getLogger().error(ex.getMessage(), ex);
			return createCancable( null);
		}

		TimeUnit unit = TimeUnit.MILLISECONDS;
		ScheduledFuture<?> schedule = executor.schedule(task, delay, unit);
		return createCancable( schedule);
	}

	private Cancelable createCancable(final ScheduledFuture<?> schedule) {
		return new Cancelable() {
			public void cancel() {
				if ( schedule != null)
				{
					schedule.cancel(true);
				}
			}
		};
	}

	@Override public void dispose()
	{
		cancel();
	}

	public Cancelable schedule(Runnable task, long delay, long period) {
		if (executor.isShutdown())
		{
			RaplaException ex = new RaplaException("Can't schedule command because executer is already shutdown " + task.toString());
			getLogger().error(ex.getMessage(), ex);
			return createCancable( null);
		}
		TimeUnit unit = TimeUnit.MILLISECONDS;
		ScheduledFuture<?> schedule = executor.scheduleAtFixedRate(task, delay, period, unit);
		return createCancable( schedule);
	}
	
	public Cancelable schedule(Command command, long delay, long period) 
	{
		Runnable task = createTask(command);
		return schedule(task, delay, period);
	}
	
	abstract class  CancableTask implements Cancelable, Runnable
    {
        long delay;
        private Runnable task;
        
        volatile Thread.State status = Thread.State.NEW;
        Cancelable cancelable;
        CancableTask next;
        
        public CancableTask(Runnable task, long delay)
        {
            this.task = task;
            this.delay = delay;
        }

        @Override
        public void run() 
        {
            try
            {
                if ( status == Thread.State.NEW)
                {
                    status = Thread.State.RUNNABLE;
                    task.run();
                }
            }
            finally
            {
                status = Thread.State.TERMINATED;
                scheduleNext();
            }
        }
        @Override
        public void cancel()
        {
            if ( cancelable != null && status == Thread.State.RUNNABLE )
            {
                // send interrupt if thread is running
                cancelable.cancel();
            }
            status = Thread.State.TERMINATED;
        }

        public void pushToEndOfQueue(CancableTask wrapper)
        {
            if ( next == null)
            {
                next = wrapper;
            }
            else
            {
                next.pushToEndOfQueue( wrapper);
            }
        }

        public void scheduleThis()
        {
            cancelable = schedule(this, delay);
        }

        private void scheduleNext()
        {
            if ( next != null)
            {
                replaceWithNext( next);
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
    
    ConcurrentHashMap<Object, CancableTask> futureTasks= new ConcurrentHashMap<Object, CancableTask>();
    @Override
    public Cancelable scheduleSynchronized(final Object synchronizationObject, Command command, final long delay)
    {
        Runnable task = createTask(command);
        return scheduleSynchronized(synchronizationObject, task, delay);
    }
    
    public Cancelable scheduleSynchronized(final Object synchronizationObject, Runnable task, final long delay)
    {
        CancableTask wrapper = new CancableTask(task,delay)
        {
            @Override
            protected void replaceWithNext(CancableTask next)
            {
                futureTasks.replace( synchronizationObject , this, next);
            }
            @Override
            protected void endOfQueueReached()
            {
                synchronized (synchronizationObject)
                {
                    futureTasks.remove( synchronizationObject);
                }
            }
        };
        synchronized (synchronizationObject)
        {
            CancableTask existing = futureTasks.putIfAbsent( synchronizationObject, wrapper);
            if (existing == null)
            {
                wrapper.scheduleThis();
            }
            else
            {
                existing.pushToEndOfQueue( wrapper );
            }
            return wrapper;
        }
    }

	public void cancel() {
		try{
			getLogger().info("Stopping scheduler thread.");
			List<Runnable> shutdownNow = executor.shutdownNow();
			for ( Runnable task: shutdownNow)
			{
				long delay = -1;
				if ( task instanceof ScheduledFuture)
				{
					ScheduledFuture scheduledFuture = (ScheduledFuture) task;
					delay = scheduledFuture.getDelay( TimeUnit.SECONDS);
				}
				if ( delay <=0)
				{
					getLogger().warn("Interrupted active task " + task );
				}
			}
			getLogger().info("Stopped scheduler thread.");
		}
		catch ( Throwable ex)
		{
			getLogger().warn(ex.getMessage());
		}
		// we give the update threads some time to execute
		try
		{
			Thread.sleep( 50);
		}
		catch (InterruptedException e) 
		{
		}
	}
}