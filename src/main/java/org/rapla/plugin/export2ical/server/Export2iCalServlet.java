package org.rapla.plugin.export2ical.server;

import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.validate.ValidationException;
import org.rapla.RaplaResources;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarNotFoundExeption;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.plugin.export2ical.Export2iCalPlugin;
import org.rapla.scheduler.Promise;
import org.rapla.server.PromiseWait;
import org.rapla.storage.StorageOperator;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;

@Path("{path:ical|internal_ical}")
@Singleton
public class Export2iCalServlet
{
	private int global_daysBefore;
	private int global_daysAfter;

//	private String username;
//	private String filename;

	private boolean global_interval;
	//private HttpServletResponse response;

	private SimpleDateFormat rfc1123DateFormat;

//  private SimpleTimeZone gmt = new SimpleTimeZone(0, "GMT");

	//private java.util.Calendar calendar;
    //private Preferences preferences;
	private final Date firstPluginStartDate = new Date(0);
	//private TimeZone pluginTimeZone;
	private int lastModifiedIntervall;
	@Inject
	Export2iCalConverter converter;
	public RaplaFacade facade;
	Logger logger;
	@Inject
	RaplaLocale raplaLocale ;
	@Inject
	RaplaResources i18n;
	@Inject
	PromiseWait promiseWait;

	@Inject
    public Export2iCalServlet()
    {
    }

	public RaplaFacade getFacade() {
		return facade;
	}

	@Inject
    void setFacade(RaplaFacade facade)
    {
        this.facade = facade;
        RaplaConfiguration config;
        try
        {
            config = facade.getSystemPreferences().getEntry(Export2iCalPlugin.ICAL_CONFIG, new RaplaConfiguration());
        }
        catch (RaplaException e)
        {
            throw new RaplaInitializationException(e.getMessage(), e);
        }
        global_interval = config.getChild(Export2iCalPlugin.GLOBAL_INTERVAL).getValueAsBoolean(Export2iCalPlugin.DEFAULT_globalIntervall);

        global_daysBefore = config.getChild(Export2iCalPlugin.DAYS_BEFORE).getValueAsInteger(Export2iCalPlugin.DEFAULT_daysBefore);
        global_daysAfter = config.getChild(Export2iCalPlugin.DAYS_AFTER).getValueAsInteger(Export2iCalPlugin.DEFAULT_daysAfter);

        rfc1123DateFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z", Locale.US);
        rfc1123DateFormat.setTimeZone(new SimpleTimeZone(0, "GMT"));

        lastModifiedIntervall = config.getChild(Export2iCalPlugin.LAST_MODIFIED_INTERVALL).getValueAsInteger(10);
    }

    @Inject
    void setLogger(Logger logger)
    {
        this.logger = logger.getChildLogger("ical");
    }

	private Logger getLogger() {
		return logger;
	}

	@GET
	@Produces({MediaType.TEXT_HTML, "text/calendar"})
	public void generatePage(@PathParam("path")  String path, @Context HttpServletRequest request, @Context HttpServletResponse response, @QueryParam("file") final String filename, @QueryParam("user") final String username) throws IOException, ServletException {

		//this.response = response;
        getLogger().debug("File: "+filename);
        getLogger().debug("User: "+username);

		boolean isAllAppointmentsSet = request.getParameter("complete") != null;
		// if param COMPLETE is given, retrieve all appointments
		StorageOperator operator = getFacade().getOperator();
		Map<String, Object> threadContextMap = operator.getThreadContextMap();
		try {
			if ( path.startsWith("internal")) {
				threadContextMap.put("internal_request", Boolean.TRUE);
			}
			final User user;
            String message = "The calendar '" + filename + "' you tried to retrieve is not published or available for the user " + username + ".";
			try
            {
				user = facade.getUser(username);
            }
            catch (EntityNotFoundException ex)
            {
				response.getWriter().println(message);
				response.getWriter().close();
				getLogger().getChildLogger("404").warn(message);
                response.setStatus( 404);
                return;
            }
            final Preferences preferences = facade.getPreferences(user);

			final CalendarModel calModel = getCalendarModel(preferences, user, filename);

            if (calModel == null) {
                response.getWriter().println(message);
				response.getWriter().close();
				response.setStatus( 404);
				getLogger().getChildLogger("404").warn(message);
                return;
            }

			response.setHeader("Last-Modified", rfc1123DateFormat.format(getLastModified(calModel)));
			final Object isSet = calModel.getOption(Export2iCalPlugin.ICAL_EXPORT);

			if((isSet == null || isSet.equals("false"))) {
				response.getWriter().println(message);
				response.getWriter().close();
				getLogger().getChildLogger("404").warn(message);
                response.setStatus( 404);
				return;
			}

			if (request.getMethod().equals("HEAD")) {
				return;
			}

			Promise<Collection<Appointment>> appointments = calModel.queryAppointments(new TimeInterval(null, null));
			write(response, promiseWait.waitForWithRaplaException(appointments, 10000), filename,user, null);
		} catch (Exception e) {
			response.getWriter().println(("An error occured giving you the Calendarview for user " + username + " named " + filename));
			response.getWriter().println();
			e.printStackTrace(response.getWriter());
			response.getWriter().close();
			getLogger().error( e.getMessage(), e);
		} finally {
			threadContextMap.remove("internal_request");
		}
	}


	/**
	 * Retrieves CalendarModel by username && filename, sets appropriate before
	 * and after times (only if global intervall is false)
	 *
	 * @param user
	 *            the Rapla-User
	 * @param filename
	 *            the Filename of the exported view
	 * @return
	 */
	private CalendarModel getCalendarModel(Preferences preferences, User user, String filename) {
		try {
			final CalendarSelectionModel calModel = facade.newCalendarModel(user);
			calModel.load(filename);

			int daysBefore = global_interval ? global_daysBefore : preferences.getEntryAsInteger(Export2iCalPlugin.PREF_BEFORE_DAYS, global_daysBefore);
			int daysAfter = global_interval ? global_daysAfter : preferences.getEntryAsInteger(Export2iCalPlugin.PREF_AFTER_DAYS, global_daysAfter);

			final Date now = new Date();

			//calModel.getReservations(startDate, endDate)


			// set start Date
			calModel.setStartDate(DateTools.add(now, DateTools.IncrementSize.DAY_OF_YEAR,-daysBefore));

			// set end Date
			calModel.setEndDate(DateTools.add(now, DateTools.IncrementSize.DAY_OF_YEAR,daysAfter));

			//debug sysout
			//System.out.println("startdate - before  "+ calModel.getStartDate() + " - " + daysBefore);
			//System.out.println("enddate - after "+ calModel.getStartDate() + " - " + daysAfter);

			return calModel;
		} catch (CalendarNotFoundExeption ex) {
			return null;
		} catch (RaplaException e) {
			getLogger().getChildLogger("404").error("The Calendarmodel " + filename + " could not be read for the user " + user + " due to " + e.getMessage());
			return null;
		} catch (NullPointerException e) {
			return null;
		}
	}

	private void write(final HttpServletResponse response, final Collection<Appointment> appointments, String filename, User user,final Preferences preferences) throws RaplaException, IOException {

		if (filename == null ) {
			filename = i18n.getString("default");
		}
		//response.setContentType("text/calendar; charset=" + raplaLocale.getCharsetNonUtf());
		response.setContentType("text/calendar; charset=UTF-8");
		response.setHeader("Content-Disposition", "attachment; filename=" + filename + ".ics");
		response.setCharacterEncoding("UTF-8");
		if (appointments == null) {
			throw new RaplaException("Error with returning '" + filename);
		}
		final Calendar iCal = converter.createiCalender(appointments,preferences, user);
		final CalendarOutputter calOutputter = new CalendarOutputter();
		final PrintWriter responseWriter = response.getWriter();
		try {
			calOutputter.output(iCal, responseWriter);
		} catch (ValidationException e) {
			getLogger().error("The calendar file is invalid!\n" + e);
		} finally {
			responseWriter.close();
		}
	}

	/**
	 * Calculates Global-Lastmod By modulo operations, this returns a fresh
	 * last-mod every n days n can be set for all users in the last-modified
	 * intervall settings
	 *
	 * @return
	 */
	private Date getGlobalLastModified()
	{
		if (lastModifiedIntervall == -1) {
			return firstPluginStartDate;
		}
		long nowInMillis = DateTools.cutDate(new Date()).getTime();
		long daysSinceStart = (nowInMillis - firstPluginStartDate.getTime()) / DateTools.MILLISECONDS_PER_DAY;
		return new Date(nowInMillis - (daysSinceStart % lastModifiedIntervall) * DateTools.MILLISECONDS_PER_DAY);
	}

	/**
	 * Get last modified if a list of allocatables
	 *
	 */
	private Date getLastModified(CalendarModel calModel) throws RaplaException {

		Date endDate = null;
	Date startDate = facade.today();
	final Promise<Collection<Reservation>> reservationsPromise = calModel.queryReservations(new TimeInterval(startDate, endDate));
		final Collection<Reservation> reservations = promiseWait.waitForWithRaplaException(reservationsPromise, 10000);
		// set to minvalue
		Date maxDate = new Date();
		maxDate.setTime(0);

		for (Reservation r:reservations) {
			Date lastMod = r.getLastChanged();

			if (lastMod != null && maxDate.before(lastMod)) {
				maxDate = lastMod;
			}
		}

		if (lastModifiedIntervall != -1 && DateTools.countDays(maxDate, new Date()) < lastModifiedIntervall) {
			return maxDate;
		} else {
			return getGlobalLastModified();
		}

	}


}
