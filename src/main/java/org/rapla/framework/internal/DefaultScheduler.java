package org.rapla.framework.internal;

import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import org.rapla.framework.Disposable;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.logger.RaplaBootstrapLogger;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.Observable;
import org.rapla.scheduler.sync.UtilConcurrentCommandScheduler;
import org.rapla.server.TimeZoneConverter;
import org.rapla.server.internal.TimeZoneConverterImpl;
import org.reactivestreams.Publisher;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static java.time.temporal.ChronoUnit.MILLIS;

@DefaultImplementation(of=CommandScheduler.class,context = {InjectionContext.server})
@Singleton
public class DefaultScheduler extends UtilConcurrentCommandScheduler implements Disposable
{
	final private TimeZoneConverter converter;

	@Inject
	public DefaultScheduler(Logger logger, TimeZoneConverter converter) {
	    this(logger, converter,6);
	}

	public DefaultScheduler(Logger logger) {
		this(logger,new TimeZoneConverterImpl());
	}

	public DefaultScheduler(Logger logger, TimeZoneConverter converter,int poolSize) {
	    super(logger,poolSize);
	    this.converter = converter;
	}

	@Override public void dispose()
	{
		cancel();
	}


	public io.reactivex.disposables.Disposable scheduleAtGivenTime(Action task, int hour,int minute)
	{
		Supplier<Long> delayProvider = ()->millisToNextPeriod( converter.getImportExportTimeZone(), hour,minute);
		return scheduleAtGivenTime( task, delayProvider  );
	}

	public io.reactivex.disposables.Disposable scheduleAtGivenTime(Action task, Supplier<Long> delayProvider)
	{
		final Observable<Long> map = just(0l).flatMap((dummy) -> just(0l).delay(delayProvider.get())).map((t) ->
		{
			task.run();
			return 0l;
		});
		io.reactivex.disposables.Disposable subscribe = map.onErrorResumeNext( (ex)->logger.error(ex.getMessage(),ex)).repeat().subscribe();
		return subscribe;
	}


	static Logger TEST_LOGGER;
	public static void main(String[] args) throws Exception {
		AtomicInteger i = new AtomicInteger(0);
		TimeZoneConverterImpl converter =  new TimeZoneConverterImpl();
		converter.setImportExportTimeZone( TimeZone.getTimeZone("Europe/Berlin"));
		TEST_LOGGER = RaplaBootstrapLogger.createRaplaLogger();
		final DefaultScheduler scheduler = new DefaultScheduler(TEST_LOGGER, converter);
		Calendar cal = Calendar.getInstance(converter.getImportExportTimeZone());
		Clock clock = Clock.system(ZoneId.of("Europe/Berlin"));
		Action action = ()->
		{
			final int i1 = i.addAndGet(1);

			TEST_LOGGER.info("Execution of task " + i1 + " started at " + LocalTime.now(clock));
			if ( i1 == 3)
			{
				throw new IllegalStateException("Error with task "+ i1);
			}
			Thread.sleep( (long)(3000 * Math.random()));
			TEST_LOGGER.info("Execution of task " + i1  +" finished");
		};

		//Function<Long,Long> delayProvider = currentTime -> Math.round(Math.random() * 1000);
		//Function<Long,Long> delayProvider = currentTime -> 2000 - (currentTime % 2000) + 500 ;

		Supplier<Long> delayProvider = ()->millisToNextPeriod( converter.getImportExportTimeZone());
		//scheduler.scheduleAtGivenTime( action, delayProvider  );
		scheduler.scheduleAtGivenTime( action, 22,25  );
		Thread.sleep(1000000);
	}


	private long millisToNextPeriod(Calendar calendar, int hourOfDay, int minute) {
		long now = calendar.getTimeInMillis();
		Calendar clone = (Calendar)calendar.clone();

		clone.set(Calendar.HOUR_OF_DAY, hourOfDay);
		clone.set(Calendar.MINUTE, minute);
		long millis = clone.getTimeInMillis() - now;

		if ( millis < 0 )
		{
			clone.add(Calendar.DATE,1);
			millis =  clone.getTimeInMillis() - now;
//            int offsetNow = calendar.get(Calendar.DST_OFFSET);
//            int offsetTommorow = clone.get(Calendar.DST_OFFSET);
//            int offsetDiff = offsetTommorow - offsetNow;
		}
		return millis;
	}

	static private long millisToNextPeriod(TimeZone timeZone) {
		Calendar calendar = Calendar.getInstance(timeZone);
		long now = calendar.getTimeInMillis();
		Calendar clone = (Calendar)calendar.clone();

		clone.set(Calendar.MILLISECOND, 0);
		long millis = clone.getTimeInMillis() - now;

		if ( millis < 0 )
		{
			clone.add(Calendar.SECOND,1);
			millis =  clone.getTimeInMillis() - now;
//            int offsetNow = calendar.get(Calendar.DST_OFFSET);
//            int offsetTommorow = clone.get(Calendar.DST_OFFSET);
//            int offsetDiff = offsetTommorow - offsetNow;
		}
		return millis;
	}

	static private long millisToNextPeriod(TimeZone timeZone, int hour, int minute) {
		Calendar calendar = Calendar.getInstance(timeZone);
		long now = calendar.getTimeInMillis();
		Calendar clone = (Calendar)calendar.clone();

		clone.set(Calendar.HOUR_OF_DAY, hour);
		clone.set(Calendar.MINUTE, minute);
		clone.set(Calendar.SECOND,0);
		clone.set(Calendar.MILLISECOND,0);
		long millis = clone.getTimeInMillis() - now;

		if ( millis < 0 )
		{
			clone.add(Calendar.DATE,1);
			millis =  clone.getTimeInMillis() - now;
			//            int offsetNow = calendar.get(Calendar.DST_OFFSET);
			//            int offsetTommorow = clone.get(Calendar.DST_OFFSET);
			//            int offsetDiff = offsetTommorow - offsetNow;
		}
		return millis;
	}

	static private Long millisToNextPeriod(Clock clock) {
		ZonedDateTime now = ZonedDateTime.now( clock);
		ZonedDateTime time = now.truncatedTo(ChronoUnit.SECONDS);

		long millis = MILLIS.between( now, time);
		if ( millis < 0 )
		{
			ZonedDateTime tommorowTime = time.plus(Duration.ofSeconds(1));
			millis = MILLIS.between( now, tommorowTime);
		}
		//TEST_LOGGER.info("Millis to next execution " + millis);
		return millis;
	}

	static private Long millisToNextPeriod(Clock clock,int hour, int minute) {
		ZonedDateTime now = ZonedDateTime.now( clock);
		ZonedDateTime time = now.withHour( hour).withMinute( minute).truncatedTo( ChronoUnit.SECONDS);
		long millis = MILLIS.between( now, time);
		if ( millis < 0 )
		{
			ZonedDateTime tommorowTime = time.plus(Duration.ofDays(1));
			millis = MILLIS.between( now, tommorowTime);
		}
		//TEST_LOGGER.info("Millis to next execution " + millis);
		return millis;
	}
}