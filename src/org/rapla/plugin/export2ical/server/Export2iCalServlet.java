package org.rapla.plugin.export2ical.server;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.SimpleTimeZone;
import java.util.TimeZone;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.ValidationException;

import org.rapla.components.util.DateTools;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarNotFoundExeption;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaDefaultContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.plugin.export2ical.Export2iCalPlugin;
import org.rapla.server.TimeZoneConverter;
import org.rapla.servletpages.RaplaPageGenerator;

public class Export2iCalServlet extends RaplaComponent implements RaplaPageGenerator {

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
	private Date firstPluginStartDate = new Date(0);
	//private TimeZone pluginTimeZone;
	private int lastModifiedIntervall;

	
    public Export2iCalServlet(RaplaContext context) throws RaplaException{
		super(createLoggerContext(context));
		RaplaConfiguration config = context.lookup(ClientFacade.class).getSystemPreferences().getEntry(Export2iCalPlugin.ICAL_CONFIG, new RaplaConfiguration());
		global_interval = config.getChild(Export2iCalPlugin.GLOBAL_INTERVAL).getValueAsBoolean(Export2iCalPlugin.DEFAULT_globalIntervall);

		global_daysBefore = config.getChild(Export2iCalPlugin.DAYS_BEFORE).getValueAsInteger(Export2iCalPlugin.DEFAULT_daysBefore);
		global_daysAfter = config.getChild(Export2iCalPlugin.DAYS_AFTER).getValueAsInteger(Export2iCalPlugin.DEFAULT_daysAfter);


		rfc1123DateFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z", Locale.US);
		rfc1123DateFormat.setTimeZone(new SimpleTimeZone(0, "GMT"));

		lastModifiedIntervall = config.getChild(Export2iCalPlugin.LAST_MODIFIED_INTERVALL).getValueAsInteger(10);
    }
    
	public static RaplaContext createLoggerContext(RaplaContext context) throws RaplaContextException
	{
		Logger logger = context.lookup(Logger.class);
		RaplaDefaultContext newContext = new RaplaDefaultContext( context);
		newContext.put(Logger.class, logger.getChildLogger("ical"));
		return newContext;
	}

	public void generatePage(ServletContext servletContext, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

		//this.response = response;

		final String filename = request.getParameter("file");
		final String username = request.getParameter("user");
        getLogger().debug("File: "+filename);
        getLogger().debug("User: "+username);

		boolean isAllAppointmentsSet = request.getParameter("complete") != null;
		// if param COMPLETE is given, retrieve all appointments

		try {
            final User user;
            String message = "The calendar '" + filename + "' you tried to retrieve is not published or available for the user " + username + ".";
			try
            {
            	user = getQuery().getUser(username);
            }
            catch (EntityNotFoundException ex)
            {
            	response.getWriter().println(message);
    			getLogger().getChildLogger("404").warn(message);
                response.setStatus( 404);
                return;
            }
            final Preferences preferences = getQuery().getPreferences(user);

			final CalendarModel calModel = getCalendarModel(preferences, user, filename);

            if (calModel == null) {
                response.getWriter().println(message);
    		    response.setStatus( 404);
    			getLogger().getChildLogger("404").warn(message);
                return;
            }

			response.setHeader("Last-Modified", rfc1123DateFormat.format(getLastModified(calModel)));
			final Object isSet = calModel.getOption(Export2iCalPlugin.ICAL_EXPORT);
            
			if((isSet == null || isSet.equals("false")))
			{
				response.getWriter().println(message);
    			getLogger().getChildLogger("404").warn(message);
                response.setStatus( 404);
				return;
			}
			
			if (request.getMethod().equals("HEAD")) {
				return;
			}

			final Reservation[] reserv = isAllAppointmentsSet ? getAllReservations(calModel) : calModel.getReservations();
			Allocatable[] allocatables = calModel.getSelectedAllocatables();
			Collection<Appointment> appointments = AppointmentImpl.getAppointments(Arrays.asList( reserv), Arrays.asList(allocatables));
			write(response, user,appointments, filename, null);
		} catch (Exception e) {
			response.getWriter().println(("An error occured giving you the Calendarview for user " + username + " named " + filename));
			response.getWriter().println();
			e.printStackTrace(response.getWriter());
			getLogger().error( e.getMessage(), e);
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
			final CalendarSelectionModel calModel = getClientFacade().newCalendarModel( user);
			calModel.load(filename);

			int daysBefore = global_interval ? global_daysBefore : preferences.getEntryAsInteger(Export2iCalPlugin.PREF_BEFORE_DAYS, 11);
			int daysAfter = global_interval ? global_daysAfter : preferences.getEntryAsInteger(Export2iCalPlugin.PREF_AFTER_DAYS, global_daysAfter);

			final Date now = new Date();

			//calModel.getReservations(startDate, endDate)

            final RaplaLocale raplaLocale = getRaplaLocale();
            final java.util.Calendar calendar = raplaLocale.createCalendar();

			// set start Date
            calendar.setTime(now);
			calendar.add(java.util.Calendar.DAY_OF_YEAR, -daysBefore);
			calModel.setStartDate(calendar.getTime());

			// set end Date
			calendar.setTime(now);
			calendar.add(java.util.Calendar.DAY_OF_YEAR, daysAfter);
			calModel.setEndDate(calendar.getTime());
			
			//debug sysout
			//System.out.println("startdate - before  "+ calModel.getStartDate() + " - " + daysBefore);
			//System.out.println("enddate - after "+ calModel.getStartDate() + " - " + daysAfter);

			return calModel;
		}
		catch (CalendarNotFoundExeption ex)
		{
			return null;
		}
		catch (RaplaException e) 
		{
			getLogger().getChildLogger("404").error("The Calendarmodel " + filename + " could not be read for the user " + user + " due to " + e.getMessage());
			return null;
		} catch (NullPointerException e) 
		{
			return null;
		}
	}

	private Reservation[] getAllReservations(final CalendarModel calModel) throws RaplaException {
        final RaplaLocale raplaLocale = getRaplaLocale();
        final java.util.Calendar calendar = raplaLocale.createCalendar();

		calendar.set(calendar.getMinimum(java.util.Calendar.YEAR), calendar.getMinimum(java.util.Calendar.MONTH), calendar.getMinimum(java.util.Calendar.DAY_OF_MONTH));
		calModel.setStartDate(calendar.getTime());

		// Calendar.getMaximum doesn't work with iCal4j. Using 9999
		calendar.set(9999, calendar.getMaximum(java.util.Calendar.MONTH), calendar.getMaximum(java.util.Calendar.DAY_OF_MONTH));
		calModel.setEndDate(calendar.getTime());

		return calModel.getReservations();
	}

	private void write(final HttpServletResponse response,User user, final Collection<Appointment> appointments, String filename, final Preferences preferences) throws RaplaException, IOException {

	    if (filename == null )
	    {
	        filename = getString("default");
	    }
		RaplaLocale raplaLocale = getRaplaLocale();
		response.setContentType("text/calendar; charset=" + raplaLocale.getCharsetNonUtf());
		response.setHeader("Content-Disposition", "attachment; filename=" + filename + ".ics");

		if (appointments == null) {
			throw new RaplaException("Error with returning '" + filename);
		}
		final RaplaContext context = getContext();
		TimeZone timezone = context.lookup( TimeZoneConverter.class).getImportExportTimeZone();
		final Export2iCalConverter converter = new Export2iCalConverter(context,timezone, preferences);
		final Calendar iCal = converter.createiCalender(appointments, user);
		final CalendarOutputter calOutputter = new CalendarOutputter();
		final PrintWriter responseWriter = response.getWriter();
		try {
			calOutputter.output(iCal, responseWriter);
		} catch (ValidationException e) {
			getLogger().error("The calendar file is invalid!\n" + e);
		} finally
		{
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
	public Date getGlobalLastModified() 
	{
		if (lastModifiedIntervall == -1) 
		{
			return firstPluginStartDate;
		}
		java.util.Calendar calendar = getRaplaLocale().createCalendar();
		long nowInMillis = DateTools.cutDate(new Date()).getTime();
		long daysSinceStart = (nowInMillis - firstPluginStartDate.getTime()) / DateTools.MILLISECONDS_PER_DAY;
		calendar.setTimeInMillis(nowInMillis - (daysSinceStart % lastModifiedIntervall) * DateTools.MILLISECONDS_PER_DAY);
		return calendar.getTime();
	}

	/**
	 * Get last modified if a list of allocatables
	 * 
	 * @param allocatable
	 * @return
	 */
	public  Date getLastModified(CalendarModel calModel) throws RaplaException {

		Date endDate = null;
        Date startDate = getClientFacade().today();
        final Reservation[] reservations = calModel.getReservations(startDate, endDate);
		
		// set to minvalue
		Date maxDate = new Date();
		maxDate.setTime(0);

		for (Reservation r:reservations) 
		{
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
