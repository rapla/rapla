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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.rapla.components.calendarview.html.AbstractHTMLView;
import org.rapla.components.util.ParseDateException;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.entities.NamedComparator;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarOptions;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;
import org.rapla.servletpages.RaplaPageGenerator;

public abstract class AbstractHTMLCalendarPage extends RaplaComponent implements RaplaPageGenerator
{
    protected AbstractHTMLView view;
    protected String calendarviewHTML;
    protected CalendarModel model = null;
    RaplaBuilder builder;

    public AbstractHTMLCalendarPage(RaplaContext context, CalendarModel calendarModel) {
        super( context);
        this.model = calendarModel.clone();
    }

    protected RaplaBuilder createBuilder() throws RaplaException {
        RaplaBuilder builder = new HTMLRaplaBuilder( getContext());
        Date startDate = view.getStartDate();
		Date endDate = view.getEndDate();
		builder.setFromModel( model, startDate, endDate  );
		builder.setNonFilteredEventsVisible( false);
        return builder;
    }

    abstract protected AbstractHTMLView createCalendarView();
    abstract protected int getIncrementSize();

    public List<Allocatable> getSortedAllocatables() throws RaplaException
	 {
	        Allocatable[] selectedAllocatables = model.getSelectedAllocatables();
	    	List<Allocatable> sortedAllocatables = new ArrayList<Allocatable>( Arrays.asList( selectedAllocatables));
	        Collections.sort(sortedAllocatables, new NamedComparator<Allocatable>( getLocale() ));
	        return sortedAllocatables;
	 }
    
    public String getCalendarHTML() {
        return calendarviewHTML;
    }

    public String getDateChooserHTML( Date date) {
        Calendar calendar = getRaplaLocale().createCalendar();
        calendar.setTime( date );
        return HTMLDateComponents.getDateSelection( "", calendar,getLocale());
    }

    public Date getStartDate() {
        return view.getStartDate();
    }

    public Date getEndDate() {
        return view.getEndDate();
    }

    public String getTitle() {
        return model.getNonEmptyTitle();
    }

    public int getDay( Date date) {
        Calendar calendarview = getRaplaLocale().createCalendar();
        calendarview.setTime( date);
        return calendarview.get( Calendar.DATE);
    }

    public int getMonth( Date date) {
        Calendar calendarview = getRaplaLocale().createCalendar();
        calendarview.setTime( date);
        return calendarview.get( Calendar.MONTH) + 1;
    }

    public int getYear( Date date) {
        Calendar calendarview = getRaplaLocale().createCalendar();
        calendarview.setTime( date);
        return calendarview.get( Calendar.YEAR);
    }
    
    abstract protected void configureView() throws RaplaException;

    protected int getIncrementAmount(int incrementSize) 
    {
        if (incrementSize == Calendar.WEEK_OF_YEAR)
        {
            int daysInWeekview = getCalendarOptions().getDaysInWeekview();
            return Math.max(1,daysInWeekview / 7 );
        }
        return 1;
    }
    
    public void generatePage( ServletContext context,HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        response.setContentType("text/html; charset=" + getRaplaLocale().getCharsetNonUtf() );
        java.io.PrintWriter out = response.getWriter();

        Calendar calendarview = getRaplaLocale().createCalendar();
        calendarview.setTime( model.getSelectedDate() );
        if ( request.getParameter("today") != null ) {
            Date today = getQuery().today();
			calendarview.setTime( today );
        } else if ( request.getParameter("day") != null ) {
            String dateString = removeXSS(request.getParameter("year") + "-"
                               + request.getParameter("month") + "-"
                               + request.getParameter("day"));
            
            try {
                SerializableDateTimeFormat format = getRaplaLocale().getSerializableFormat();
                calendarview.setTime( format.parseDate( dateString, false ) );
            } catch (ParseDateException ex) {
                out.close();
                throw new ServletException( ex);
            }
            int incrementSize = getIncrementSize();
            if ( request.getParameter("next") != null)
                calendarview.add( incrementSize, getIncrementAmount(incrementSize));
            if ( request.getParameter("prev") != null)
                calendarview.add( incrementSize, -getIncrementAmount(incrementSize));
        }

        Date currentDate = calendarview.getTime();
        model.setSelectedDate( currentDate );
        view = createCalendarView();
        try {
        	configureView();
        } catch (RaplaException ex) {
            getLogger().error("Can't configure view ", ex);
            throw new ServletException( ex );
        }
        view.setLocale( getRaplaLocale().getLocale() );
        view.setTimeZone(getRaplaLocale().getTimeZone());
        view.setToDate(model.getSelectedDate());
        model.setStartDate( view.getStartDate() );
        model.setEndDate( view.getEndDate() );

        try {
            builder = createBuilder();
        } catch (RaplaException ex) {
            getLogger().error("Can't create builder ", ex);
            out.close();
            throw new ServletException( ex );
        }
        view.rebuild( builder);

        printPage(request, out, calendarview);
	}

    /**
     * @throws ServletException  
     * @throws UnsupportedEncodingException 
     */
    protected void printPage(HttpServletRequest request, java.io.PrintWriter out, Calendar currentDate) throws ServletException, UnsupportedEncodingException {
        boolean navigationVisible = isNavigationVisible( request );
        String linkPrefix = request.getPathTranslated() != null ? "../": "";
		        
        RaplaLocale raplaLocale= getRaplaLocale();
        calendarviewHTML = view.getHtml();
        out.println("<!DOCTYPE html>"); // we have HTML5 
		out.println("<html>");
		out.println("<head>");
		out.println("  <title>" + getTitle() + "</title>");
		out.println("  <link REL=\"stylesheet\" href=\""+linkPrefix + "calendar.css\" type=\"text/css\">");
		out.println("  <link REL=\"stylesheet\" href=\"" + linkPrefix + "default.css\" type=\"text/css\">");
		// tell the html page where its favourite icon is stored
		out.println("    <link REL=\"shortcut icon\" type=\"image/x-icon\" href=\""+linkPrefix + "images/favicon.ico\">");
		out.println("  <meta HTTP-EQUIV=\"Content-Type\" content=\"text/html; charset=" + raplaLocale.getCharsetNonUtf() + "\">");
		out.println("</head>");
		out.println("<body>");
		if (request.getParameter("selected_allocatables") != null && request.getParameter("allocatable_id")==null)
		{
            try {
                Allocatable[] selectedAllocatables = model.getSelectedAllocatables();
                printAllocatableList(request, out, getLocale(), selectedAllocatables);
            } catch (RaplaException e) {
                throw new ServletException(e);
            }
		}
		else
		{
		    String allocatable_id  = request.getParameter("allocatable_id");
		    // Start DateChooser
			if (navigationVisible)
			{
				out.println("<div class=\"datechooser\">");
				out.println("<form action=\""+linkPrefix + "rapla\" method=\"get\">");
				String keyParamter = request.getParameter("key");
				if (keyParamter != null)
				{
					out.println(getHiddenField("key", keyParamter));
				}
				else
				{
					out.println(getHiddenField("page", "calendar"));
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
				out.println(getDateChooserHTML(currentDate.getTime()));
				// add the "goto" button including the css class="super button"
				out.println("<span class=\"button\"><input type=\"submit\" name=\"goto\" value=\"" + getString("goto_date") + "\"/></span>");
				out.println("<span class=\"spacer\">&nbsp;</span>");
				out.println("<span class=\"spacer\">&nbsp;</span>");
				// add the "today" button including the css class="super button"
				out.println("<span class=\"button\"><input type=\"submit\" name=\"today\" value=\"" + getString("today") + "\"/></span>");
				out.println("<span class=\"spacer\">&nbsp;</span>");
				// add the "next" button including the css class="super button"
				out.println("<span class=\"button\"><input type=\"submit\" name=\"next\" value=\"&gt;&gt;\"/></span>");
				out.println("</form>");
				out.println("</div>");
			}
			
			// End DateChooser
			// Start weekview
			out.println("<h2 class=\"title\">");
			out.println(getTitle());
			out.println("</h2>");
			out.println("<div id=\"calendar\">");
			out.println(getCalendarHTML());
			out.println("</div>");
			
			// end weekview
		}
		out.println("</body>");
		out.println("</html>");
    }

    static public void printAllocatableList(HttpServletRequest request, java.io.PrintWriter out, Locale locale, Allocatable[] selectedAllocatables) throws UnsupportedEncodingException {
    	out.println("<table>");
    	String base = request.getRequestURL().toString();
    	String queryPath = request.getQueryString();
    	queryPath = queryPath.replaceAll("&selected_allocatables[^&]*","");
    	List<Allocatable> sortedAllocatables = new ArrayList<Allocatable>(Arrays.asList(selectedAllocatables));
    	Collections.sort( sortedAllocatables, new NamedComparator<Allocatable>(locale) );
    	for (Allocatable alloc:sortedAllocatables)
    	{
    		out.print("<tr>");
    		out.print("<td>");
    		String name = alloc.getName(locale);
    		out.print(name);
    		out.print("</a>");
    		out.print("</td>");
    		out.print("<td>");
    		String link = base + "?" + queryPath + "&allocatable_id=" +  URLEncoder.encode(alloc.getId(),"UTF-8");
    		out.print("<a href=\""+ link+ "\">");
    		out.print(link);
    		out.print("</a>");
    		out.print("</td>");
    		out.print("</tr>");
    		
    	}
    	out.println("</table>");
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

    String getHiddenField( String fieldname, String value) {
        // prevent against css attacks
        value = removeXSS(value);
        return "<input type=\"hidden\" name=\"" + fieldname + "\" value=\"" + value + "\"/>";
    }

    private String removeXSS(String value) {
        return value.replaceAll("<","&lt;").replaceAll(">", "&gt;").replaceAll("\"", "'");
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
       return getCalendarOptions(user);
    }

}

