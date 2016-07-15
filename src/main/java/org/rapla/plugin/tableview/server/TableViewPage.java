package org.rapla.plugin.tableview.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.StringTokenizer;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.CalendarModel;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.plugin.abstractcalendar.server.AbstractHTMLCalendarPage;
import org.rapla.plugin.tableview.RaplaTableColumn;

abstract public class TableViewPage<T, C> 
{

	protected CalendarModel model;

	private final RaplaLocale raplaLocale;

	public TableViewPage(RaplaLocale raplaLocale ) {
		this.raplaLocale = raplaLocale;
	}


	public String getTitle() {
	    return model.getNonEmptyTitle();
	}

	public void generatePage(ServletContext context, HttpServletRequest request, HttpServletResponse response, CalendarModel model)
			throws ServletException, IOException 
	{
		this.model = model.clone();
	    response.setContentType("text/html; charset=" + raplaLocale.getCharsetNonUtf() );
	    java.io.PrintWriter out = response.getWriter();
	
	    String linkPrefix = request.getPathTranslated() != null ? "../": "";
		
	    out.println("<html>");
	    out.println("<head>");
	    out.println("  <title>" + getTitle() + "</title>");
	    out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
	    out.println("  <link REL=\"stylesheet\" href=\""+linkPrefix + "bootstrap.min.css\" type=\"text/css\">");
	    out.println("  <link REL=\"stylesheet\" href=\""+linkPrefix + "calendar.css\" type=\"text/css\">");
	    out.println("  <link REL=\"stylesheet\" href=\""+linkPrefix + "default.css\" type=\"text/css\">");
	    out.println("  <link REL=\"stylesheet\" href=\""+linkPrefix + "rapla.css\" type=\"text/css\">");
	    out.println("  <link REL=\"stylesheet\" href=\""+linkPrefix + "export.css\" type=\"text/css\">");
	    // tell the html page where its favourite icon is stored
	    out.println("    <link REL=\"shortcut icon\" type=\"image/x-icon\" href=\"/images/favicon.ico\">");
	    out.println("  <meta HTTP-EQUIV=\"Content-Type\" content=\"text/html; charset=" + raplaLocale.getCharsetNonUtf() + "\">");
	    out.println("</head>");
	    out.println("<body>");
	    if (request.getParameter("selected_allocatables") != null && request.getParameter("allocatable_id")==null)
        {
            try {
                Collection<Allocatable> selectedAllocatables = model.getSelectedAllocatablesAsList();
                AbstractHTMLCalendarPage.printAllocatableList(request, out, raplaLocale.getLocale(), selectedAllocatables);
            } catch (RaplaException e) {
                throw new ServletException(e);
            }
        }
        else
        {
    	    out.println("<h2 class=\"title\">");
    	    out.println(getTitle());
    	    out.println("</h2>");
    	    out.println("<div id=\"calendar\">");
    	    try {
    	        final String calendarHTML = getCalendarHTML();
    	        out.println(calendarHTML);
    	    } catch (RaplaException e) {
    	        out.close();
    	        throw new ServletException( e);
    	    }
    	    out.println("</div>");
        }	    
	    // end weekview
	    out.println("</body>");
	    out.println("</html>");
	    out.close();
	    
	}
	class TableRow implements Comparable<TableRow>
	{
        T object;
        @SuppressWarnings("rawtypes")
        RaplaTableColumn reservationColumnPlugins;
        int direction;
        TableRow(T originalObject, RaplaTableColumn<T, C> reservationColumnPlugins, int sortDirection)
        {
            this.object = originalObject;
            this.reservationColumnPlugins = reservationColumnPlugins;
            this.direction = sortDirection;
        }
        
        
        @SuppressWarnings({ "rawtypes", "unchecked" })
        public int compareTo(TableRow o) {
            if (o.equals( this))
            {
                return 0;
            }
            if ( reservationColumnPlugins != null)
            {
                Object v1 = reservationColumnPlugins.getValue( object );
                Object v2 = o.reservationColumnPlugins.getValue( o.object);
                if ( v1 != null && v2 != null)
                {
                    Class<?> columnClass = reservationColumnPlugins.getColumnClass();
                    if ( columnClass.equals( String.class))
                    {
                        return String.CASE_INSENSITIVE_ORDER.compare( v1.toString(), v2.toString()) * direction;
                    }
                    else if (columnClass.isAssignableFrom(Comparable.class))
                    {
                        return ((Comparable)v1).compareTo( v2) * direction;
                    }
                }
            }
            T object1 = object;
            T object2 = o.object;
            return TableViewPage.this.compareTo(object1,object2);
        }
       
    }
	 

	public  String getCalendarHTML(List< RaplaTableColumn<T, C>> columPluigns, Collection<T> rowObjects,String sortingStringOption) {

	       RaplaTableColumn<T, C> columPlugin = null;
	       int sortDirection =1;
	       String sorting = model.getOption(sortingStringOption);
	       if ( sorting != null)
	       {
	          Enumeration<Object> e = new StringTokenizer( sorting,";", false);
	          for (Object stringToCast:Collections.list(e))
	          {
	              String string = (String) stringToCast;
	              int length = string.length();
	              int column = Integer.parseInt(string.substring(0,length-1));
	              char order = string.charAt( length-1);
	              if ( columPluigns.size() > column)
	              {
	                  columPlugin = columPluigns.get( column ); 
	                  sortDirection= order == '+' ? 1: -1;
	              }
	          }
	       }
	       List<TableRow> rows = new ArrayList<TableRow>();
	       for (T r :rowObjects)
	       {
	           rows.add( new TableRow( r, columPlugin, sortDirection));
	       }
	       Collections.sort( rows);
	       
	       StringBuffer buf = new StringBuffer();
	       buf.append("<table class='export table table-striped table-bordered' style='width: 99%; margin: 0 auto;'>");
	       buf.append("<thead><tr>");
	       for (RaplaTableColumn<?, C> col: columPluigns)
	       {
	           buf.append("<th>");
	           buf.append(col.getColumnName());
	           buf.append("</th>");
	       }
	       buf.append("</tr></thead>");
	       buf.append("<tbody>");
	       for (TableRow row :rows)
	       {
	           buf.append("<tr>");
	           for (RaplaTableColumn<T, C> col: columPluigns)
	           {
	               buf.append("<td>");
	               T rowObject = row.object;
	               final String htmlValue = col.getHtmlValue(rowObject);
                buf.append(htmlValue);
	               buf.append("</td>");
	           }
	          
	           buf.append("</tr>");
	       }
	       buf.append("</tbody>");
	       buf.append("</table>");
	       final String result = buf.toString();
	       return result;
	    }
	
	abstract String getCalendarHTML() throws RaplaException;
	abstract int compareTo(T object1, T object2);

}