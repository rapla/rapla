/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.plugin.abstractcalendar.server;

import org.rapla.RaplaResources;
import org.rapla.components.calendarview.html.AbstractHTMLView;
import org.rapla.components.i18n.I18nBundle;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.ParseDateException;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.components.util.Tools;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.entities.dynamictype.SortedClassifiableComparator;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarOptions;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.plugin.abstractcalendar.HTMLRaplaBuilder;
import org.rapla.plugin.abstractcalendar.MultiCalendarPrint;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;
import org.rapla.scheduler.Promise;
import org.rapla.server.PromiseWait;
import org.rapla.server.extensionpoints.HTMLViewPage;

import javax.inject.Inject;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public abstract class AbstractHTMLCalendarPage  implements HTMLViewPage
{
    protected AbstractHTMLView view;
    protected CalendarModel model = null;
    final protected RaplaResources raplaResources;
    final protected RaplaLocale raplaLocale;
    final protected RaplaFacade facade;
    final protected Logger logger;
    final protected AppointmentFormater appointmentFormater;
    @Inject
    protected PromiseWait promiseWait;

    public AbstractHTMLCalendarPage(RaplaLocale raplaLocale, RaplaResources raplaResources, RaplaFacade facade, Logger logger, AppointmentFormater appointmentFormater) {
        this.raplaResources = raplaResources;
        this.raplaLocale = raplaLocale;
        this.logger = logger;
        this.facade = facade;
        this.appointmentFormater = appointmentFormater;
    }

    public List<Allocatable> getSortedAllocatables() throws RaplaException
    {
        List<Allocatable> sortedAllocatables = model.getSelectedAllocatablesSorted();
        return sortedAllocatables;
    }

    protected RaplaLocale getRaplaLocale()
    {
        return raplaLocale;
    }


    public RaplaResources getI18n()
    {
        return raplaResources;
    }

    protected RaplaBuilder createBuilder() throws RaplaException {
        RaplaBuilder builder = new HTMLRaplaBuilder( raplaLocale,facade,raplaResources, logger, appointmentFormater);
        Date startDate = view.getStartDate();
		Date endDate = view.getEndDate();
        builder.setNonFilteredEventsVisible( false);
		final Promise<RaplaBuilder> initBuilder = builder.initFromModel( model, startDate, endDate  );
        final RaplaBuilder raplaBuilder = promiseWait.waitForWithRaplaException(initBuilder, 9000);
        return raplaBuilder;
    }

    abstract protected AbstractHTMLView createCalendarView() throws RaplaException;
    abstract protected DateTools.IncrementSize getIncrementSize();

    public String getCalendarHTML() {
        return view != null ? view.getHtml() : "";
    }

    public String getDateChooserHTML( Date date) {
        return HTMLDateComponents.getDateSelection("", date, raplaLocale);
    }


    public Date getStartDate() {
        return view.getStartDate();
    }

    public Date getEndDate() {
        return view.getEndDate();
    }

    public String getTitle() {
        return Tools.createXssSafeString(model.getNonEmptyTitle());
    }

    public int getDay( Date date) {
        final DateTools.DateWithoutTimezone dateWithoutTimezone = DateTools.toDate(date.getTime());
        return dateWithoutTimezone.day;
    }

    public int getMonth( Date date) {
        final DateTools.DateWithoutTimezone dateWithoutTimezone = DateTools.toDate(date.getTime());
        return dateWithoutTimezone.month;
    }

    public int getYear( Date date) {
        final DateTools.DateWithoutTimezone dateWithoutTimezone = DateTools.toDate(date.getTime());
        return dateWithoutTimezone.year;
    }
    
    abstract protected void configureView() throws RaplaException;

    protected int getIncrementAmount(DateTools.IncrementSize incrementSize)
    {
        if (incrementSize == DateTools.IncrementSize.WEEK_OF_YEAR)
        {
            int daysInWeekview = getCalendarOptions().getDaysInWeekview();
            return Math.max(1,daysInWeekview / 7 );
        }
        return 1;
    }

    @Override
    public void generatePage( ServletContext context,HttpServletRequest request, HttpServletResponse response,CalendarModel calendarModel) throws ServletException, IOException
    {
        this.model = calendarModel.clone();
        response.setContentType("text/html; charset=" + raplaLocale.getCharsetNonUtf());
        java.io.PrintWriter out = response.getWriter();

        Date calendarview = model.getSelectedDate();
        if ( request.getParameter("today") != null ) {
            Date today = facade.today();
			calendarview =  today;
        } else if ( request.getParameter("day") != null ) {
            String dateString = Tools.createXssSafeString(request.getParameter("year") + "-"
                               + request.getParameter("month") + "-"
                               + request.getParameter("day"));
            
            try {
                SerializableDateTimeFormat format = raplaLocale.getSerializableFormat();
                calendarview =  format.parseDate( dateString, false );
            } catch (ParseDateException ex) {
                out.close();
                throw new ServletException( ex);
            }
            DateTools.IncrementSize incrementSize = getIncrementSize();

            if ( request.getParameter("next") != null)
            {
                calendarview = DateTools.add(calendarview,incrementSize, getIncrementAmount(incrementSize));
            }
            if ( request.getParameter("prev") != null)
            {
                calendarview = DateTools.add(calendarview,incrementSize, -getIncrementAmount(incrementSize));
            }
        }

        Date currentDate = calendarview;
        model.setSelectedDate( currentDate );
        try {
            view = createCalendarView();
        	configureView();
        } catch (RaplaException ex) {
            logger.error("Can't configure view ", ex);
            throw new ServletException( ex );
        }
        view.setLocale( raplaLocale );

        printPage(request, out, calendarview);
        out.close();
	}

    protected void rebuild() throws ServletException {
        try {

            view.setToDate(model.getSelectedDate());
            model.setStartDate( view.getStartDate() );
            model.setEndDate( view.getEndDate() );
            RaplaBuilder builder = createBuilder();
             view.rebuild( builder);
        } catch (RaplaException ex) {
            logger.error("Can't create builder ", ex);
            throw new ServletException( ex );
        }
    }

    /**
     * @throws ServletException  
     * @throws UnsupportedEncodingException 
     */
    protected void printPage(HttpServletRequest request, java.io.PrintWriter out, Date currentDate) throws ServletException, UnsupportedEncodingException {
        boolean navigationVisible = isNavigationVisible( request );

        out.println("<!DOCTYPE html>"); // we have HTML5 
		out.println("<html>");
		out.println("<head>");
        final String title = getTitle(request);
        out.println("  <title>" + title + "</title>");
        String formAction = getUrl(request,"rapla/calendar");

        out.println("  " + getCssLine(request, "calendar.css"));
        out.println("  " + getCssLine(request, "default.css"));
		out.println("  " + getFavIconLine(request));
		String charset = "UTF-8";
		//String charset = raplaLocale.getCharsetNonUtf();
		out.println("  <meta HTTP-EQUIV=\"Content-Type\" content=\"text/html; charset=" + charset + "\">");
		out.println("</head>");
		out.println("<body>");
		if (isShowLinkList( model,request))
		{
            try {
                Collection<Allocatable> selectedAllocatables = model.getSelectedAllocatablesAsList();
                printAllocatableList(request, out, raplaLocale.getLocale(), model.getNonEmptyTitle(),selectedAllocatables, false);
            } catch (RaplaException e) {
                throw new ServletException(e);
            }
		}
		else
		{
		    String allocatable_id  = request.getParameter("allocatable_id");
            final String pageRequestParam = request.getParameter("pages");
            int selectedPageCount;
            if (pageRequestParam != null) {
               selectedPageCount = Integer.parseInt(pageRequestParam);
            } else {
                final String pages = model.getOption(CalendarModel.PAGES);
                selectedPageCount = pages != null ? Integer.parseInt(pages) : 1;
            }
		    // Start DateChooser
			if (navigationVisible)
			{
				out.println("<div class=\"datechooser\">");
				String keyParamter = request.getParameter("key");
                out.println("<form action=\"" + formAction + "\" method=\"get\" accept-charset=\""+ charset + "\">");
				
				if (keyParamter != null)
				{
					out.println(getHiddenField("key", keyParamter)) ;
                    final String salt = request.getParameter("salt");
                    if (salt != null)
                    {
                        out.println(getHiddenField("salt", salt));
                    }
                }
				else
				{
					//out.println(getHiddenField("page", "calendar"));
					out.println(getHiddenField("user", model.getUser().getUsername()));
					String filename = getFilename( request );
					if ( filename != null)
					{
						out.println(getHiddenField("file", filename));
					}
				}

	            if ( allocatable_id != null)
	            {
	                out.println(getHiddenField("allocatable_id", allocatable_id));
	            }               
				// add the "previous" button including the css class="super button"
				out.println("<span class=\"button\"><input type=\"submit\" name=\"prev\" value=\"&lt;&lt;\"/></span> ");
				out.println("<span class=\"spacer\">&nbsp;</span> ");
				out.println(getDateChooserHTML(currentDate));
				// add the "goto" button including the css class="super button"
				out.println("<span class=\"button\"><input type=\"submit\" name=\"goto\" value=\"" + getI18n().getString("goto_date") + "\"/></span>");
				out.println("<span class=\"spacer\">&nbsp;</span>");
				out.println("<span class=\"spacer\">&nbsp;</span>");
				// add the "today" button including the css class="super button"
				out.println("<span class=\"button\"><input type=\"submit\" name=\"today\" value=\"" + getI18n().getString("today") + "\"/></span>");

				// add the "next" button including the css class="super button"
				out.println("<span class=\"button\"><input type=\"submit\" name=\"next\" value=\"&gt;&gt;\"/></span>");
                out.println("<span class=\"spacer\">&nbsp;</span>");
                String incrementName = MultiCalendarPrint.getIncrementName( getIncrementSize(),getI18n());
                out.println("<span>");
                out.println(incrementName);
                out.println(HTMLDateComponents.getCountSelect("pages",selectedPageCount,1,20, true));
                out.println("</span>");
                out.println("</form>");
				out.println("</div>");
			}
			// End DateChooser
			// Start weekview
			out.println("<h2 class=\"title\">");
			out.println(title);
			out.println("</h2>");

            final Date selectedDate = model.getSelectedDate();
            Date currentPrintDate = selectedDate;
			for ( int i=0;i<selectedPageCount;i++) {
                out.println("<div class=\"calendar\">");
                int printOffset = (int) DateTools.countDays(selectedDate, currentPrintDate);
                model.setSelectedDate(DateTools.addDays(selectedDate, printOffset));
                rebuild();
                currentPrintDate = DateTools.add(currentPrintDate, getIncrementSize(), 1);
                out.println(getCalendarHTML());
                out.println("</div>");
            }

			// end weekview
		}
		out.println("</body>");
		out.println("</html>");
    }

    private String getTitle(HttpServletRequest request)
    {
        String allocatable_id  = request.getParameter("allocatable_id");

        if ( allocatable_id != null) {
            final Allocatable allocatable = facade.getOperator().tryResolve(allocatable_id, Allocatable.class);
            if ( allocatable != null)
            {
                final String name = allocatable.getName(getRaplaLocale().getLocale());
                return name;
            }
        }
        return getTitle();
    }

    static public String getFavIconLine(HttpServletRequest request)
    {
        return "<link REL=\"shortcut icon\" type=\"image/x-icon\" href=\"" + getUrl(request, "images/favicon.ico") + "\">";
    }

    static public String getCssLine(HttpServletRequest request, String cssName)
    {
        final String cssLink = getUrl(request, cssName);
        return "<link REL=\"stylesheet\" href=\"" + cssLink + "\" type=\"text/css\">";
    }

    static public String getUrl(HttpServletRequest request, String cssName)
    {
        final String contextPath = request.getServletContext().getContextPath();
        String linkPrefix =  contextPath;
        if ( !linkPrefix.startsWith("/"))
        {
            linkPrefix= "/" + linkPrefix;
        }
        if ( !linkPrefix.endsWith("/"))
        {
            linkPrefix= linkPrefix + "/";
        }
        return linkPrefix + cssName;
    }

    static public void printAllocatableList(HttpServletRequest request, java.io.PrintWriter out, Locale locale, String title, Collection<Allocatable> selectedAllocatables, boolean addCSV) throws UnsupportedEncodingException {
    	String base = request.getRequestURI();
        String forwardProto = request.getHeader("X-Forwarded-Proto");
        boolean secure = (forwardProto != null && forwardProto.equalsIgnoreCase("https")) || request.isSecure();
        if ( secure) {
            base = base.replaceAll("http://", "https://");
        }
        String queryPath = request.getQueryString();
        final String key = request.getParameter("key");
        final String salt = request.getParameter("salt");
        if ( key != null && salt != null)
        {
            queryPath = "key=" + URLEncoder.encode(key,"UTF-8") + "&salt=" + URLEncoder.encode(salt,"UTF-8");
        }
        else
        {
            queryPath = queryPath.replaceAll("&selected_allocatables[^&]*", "");
        }

        out.println("<table>");
        {
            addListRow(out, addCSV, base, title, queryPath + "&allocatable_id=");
        }
        out.println("</table>");
        out.println("<hr/>");
        out.println("<table>");
        List<Allocatable> sortedAllocatables = new ArrayList<>(selectedAllocatables);
    	Collections.sort( sortedAllocatables, new SortedClassifiableComparator(locale) );
    	for (Allocatable alloc:sortedAllocatables)
    	{
            String name = alloc.getName(locale);
            final String fullQueryPath = queryPath + "&allocatable_id=" + URLEncoder.encode(alloc.getId(), "UTF-8");
            addListRow(out, addCSV, base, name, fullQueryPath);
        }
    	out.println("</table>");
    }

    private static void addListRow(PrintWriter out, boolean addCSV, String base, String name, String fullQueryPath)
    {
        out.print("<tr>");
        out.print("<td>");
        out.print(name);
        out.print("</td>");
        {
            out.print("<td  style=\"padding-left:15px;\">");
            String link = base + "?" + fullQueryPath;
            out.print("<a href=\"" + link + "\">");
            out.print("HTML");
            out.print("</a>");
            out.print("</td>");
        }
        if ( addCSV )
        {
            out.print("<td  style=\"padding-left:15px;15px;\">");
            String link = base + ".csv?" + fullQueryPath;
            out.print("<a href=\"" + link + "\">");
            out.print("CSV");
            out.print("</a>");
        }
        out.print("</tr>");
    }

    public String getFilename(HttpServletRequest request) {
        return request.getParameter("file");
    }


    public boolean isNavigationVisible( HttpServletRequest request) {
        String config = model.getOption( CalendarModel.SHOW_NAVIGATION_ENTRY );
        if ( config == null || config.equals( "true" ))
        {
            return true;
        }
        return !config.equals( "false" ) && request.getParameter("hide_nav") == null;
    }

    static public boolean isShowLinkList( CalendarModel model,HttpServletRequest request) {
        if (request.getParameter("allocatable_id")  != null) {
            return false;
        }
        if (request.getParameter("selected_allocatables") != null ) {
            return true;
        }
        String config = model.getOption( CalendarModel.RESOURCES_LINK_LIST);
        return config !=null && config.equals( "true" ) ;
    }



    String getHiddenField( String fieldname, String value) {
        // prevent against css attacks
    	value = encodeSafe(value);
        return "<input type=\"hidden\" name=\"" + fieldname + "\" value=\"" + value + "\"/>";
    }

	public String encodeSafe(String value) {
//		try {
//			value = URLEncoder.encode(value,"UTF-8");
//		} catch (UnsupportedEncodingException e) {
//			throw new IllegalStateException(e);
//		}
        value = Tools.createXssSafeString(value);
		return value;
	}

    String getHiddenField( String fieldname, int value) {
        return getHiddenField( fieldname, String.valueOf(value));
    }

    /*
    public String getLegend() {
        if ( !getCalendarOptions().isResourceColoring()) {
            return "";
        }
        Iterator it = view.getBlocks().iterator();
        LinkedList coloredAllocatables = new LinkedList();
        while (it.hasNext()) {
            List list = ((HTMLRaplaBlock)it.next()).getContext().getColoredAllocatables();
            for (int i=0;i<list.size();i++) {
                Object obj = list.get(i);
                if (!coloredAllocatables.contains(obj))
                    coloredAllocatables.add(obj);
            }
        }
        if (coloredAllocatables.size() < 1) {
            return "";
        }
        StringBuffer buf = new StringBuffer();
        it = coloredAllocatables.iterator();
        buf.append("<table>\n");
        buf.append("<tr>\n");
        buf.append("<td>");
        buf.append( getI18n().getString("legend"));
        buf.append(":");
        buf.append("</td>");
        try {
            AllocatableInfoUI allocatableInfo = new AllocatableInfoUI(getContext());
            while (it.hasNext()) {
                Allocatable allocatable = (Allocatable) it.next();
                String color = (String) builder.getColorMap().get(allocatable);
                if (color == null) // (!color_map.containsKey(allocatable))
                    continue;

                buf.append("<td style=\"background-color:");
                buf.append( color );
                buf.append("\">");
                buf.append("<a href=\"#\">");
                buf.append( allocatable.getName(getRaplaLocale().getLocale()) );
                buf.append("<span>");
                buf.append( allocatableInfo.getTooltip( allocatable));
                buf.append("</span>");
                buf.append("</a>");
                buf.append("</td>");
            }
        } catch (RaplaException ex) {
            getLogger().error( "Error generating legend",ex);
        }
        buf.append("</tr>\n");
        buf.append("</table>");
        return buf.toString();
    }
    */

    public CalendarOptions getCalendarOptions() {
       User user = model.getUser();
       return RaplaComponent.getCalendarOptions(user, facade);
    }

}

