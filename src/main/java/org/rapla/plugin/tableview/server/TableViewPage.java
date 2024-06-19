package org.rapla.plugin.tableview.server;

import org.rapla.components.util.Tools;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.CalendarModel;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.plugin.abstractcalendar.server.AbstractHTMLCalendarPage;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.RaplaTableModel;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

abstract public class TableViewPage<T>
{

    protected CalendarModel model;

    private final RaplaLocale raplaLocale;
    private boolean csv;
    private boolean addIds;

    public TableViewPage(RaplaLocale raplaLocale)
    {
        this.raplaLocale = raplaLocale;
    }

    public String getTitle()
    {
        return model.getNonEmptyTitle();
    }

    public void generatePage(ServletContext context, HttpServletRequest request, HttpServletResponse response, CalendarModel model)
            throws ServletException, IOException
    {
        this.model = model.clone();
        final String pathTranslated = request.getPathTranslated();
        csv = pathTranslated.endsWith(".csv");
        String withId =request.getParameter("addIds");
        addIds =withId != null && withId.equals("true");
        if (csv)
        {
            generagePageCSV(request, response, model);
        }
        else
        {
            generagePageHtml(request, response, model);
        }
    }

    public boolean isCsv()
    {
        return csv;
    }

    private void generagePageCSV(HttpServletRequest request, HttpServletResponse response, CalendarModel model) throws IOException, ServletException
    {
        response.setContentType("text/comma-separated-values; charset=" + raplaLocale.getCharsetNonUtf());
        String filename = model.getFilename();
        response.setHeader("Content-Disposition","attachment; filename=\""+filename+".csv\"");
        java.io.PrintWriter out = response.getWriter();
        try
        {
            final String calendarCSV = getCalendarBody();
            out.println(calendarCSV);
        }
        catch (RaplaException e)
        {
            throw new ServletException(e);
        } finally
        {
            out.close();
        }
    }

    private void generagePageHtml(HttpServletRequest request, HttpServletResponse response, CalendarModel model) throws IOException, ServletException
    {
        response.setContentType("text/html; charset=" + raplaLocale.getCharsetNonUtf());
        java.io.PrintWriter out = response.getWriter();

        String linkPrefix = request.getPathTranslated() != null ? "../" : "";

        out.println("<html>");
        out.println("<head>");
        out.println("  <title>" + getTitle() + "</title>");
        out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        out.println("  <link REL=\"stylesheet\" href=\"" + linkPrefix + "bootstrap.min.css\" type=\"text/css\">");
        out.println("  <link REL=\"stylesheet\" href=\"" + linkPrefix + "calendar.css\" type=\"text/css\">");
        out.println("  <link REL=\"stylesheet\" href=\"" + linkPrefix + "default.css\" type=\"text/css\">");
        out.println("  <link REL=\"stylesheet\" href=\"" + linkPrefix + "rapla.css\" type=\"text/css\">");
        out.println("  <link REL=\"stylesheet\" href=\"" + linkPrefix + "export.css\" type=\"text/css\">");
        // tell the html page where its favourite icon is stored
        out.println("    <link REL=\"shortcut icon\" type=\"image/x-icon\" href=\"/images/favicon.ico\">");
        out.println("  <meta HTTP-EQUIV=\"Content-Type\" content=\"text/html; charset=" + raplaLocale.getCharsetNonUtf() + "\">");
        out.println("</head>");
        String filename = request.getParameter("file");
        String pageId = Tools.createXssSafeString( filename);
        out.println("<body id=\""+ pageId+ "\">");
        if (AbstractHTMLCalendarPage.isShowLinkList(model,request))
        {
            try
            {
                Collection<Allocatable> selectedAllocatables = model.getSelectedAllocatablesAsList();
                AbstractHTMLCalendarPage.printAllocatableList(request, out, raplaLocale.getLocale(),model.getNonEmptyTitle(), selectedAllocatables, true);
            }
            catch (RaplaException e)
            {
                throw new ServletException(e);
            }
        }
        else
        {
            out.println("<h2 class=\"title\">");
            out.println(getTitle());
            out.println("</h2>");
            out.println("<div id=\"calendar\">");
            try
            {
                final String calendarHTML = getCalendarBody();
                out.println(calendarHTML);
            }
            catch (RaplaException e)
            {
                out.close();
                throw new ServletException(e);
            }
            out.println("</div>");
        }
        // end weekview
        out.println("</body>");
        out.println("</html>");
        out.close();
    }

    public String getCalendarBody(List<RaplaTableColumn<T>> columPlugins, Collection<T> rowObjects, Map<RaplaTableColumn<T>, Integer> sortDirections) {

        //FIXME Replace with KeyNameFormat
        final List<T> rows = RaplaTableModel.sortRows(rowObjects, sortDirections, getFallbackComparator(), DynamicTypeAnnotations.KEY_NAME_FORMAT);
        if (isCsv())
        {
            String contextAnnotationName = DynamicTypeAnnotations.KEY_NAME_FORMAT;
            return RaplaTableModel.getCSV(columPlugins, rows, contextAnnotationName, addIds);
        }
        else
        {
            return  getCalendarBodyHTML(columPlugins, rows);
        }
    }

    public String getCalendarBodyHTML(List<RaplaTableColumn<T>> columPlugins, List<T> rows)
    {

        StringBuffer buf = new StringBuffer();
        buf.append("<table class='export table table-striped table-bordered' style='width: 99%; margin: 0 auto;'>");
        buf.append("<thead><tr>");
        for (RaplaTableColumn<?> col : columPlugins)
        {
            buf.append("<th>");
            buf.append(col.getColumnName());
            buf.append("</th>");
        }
        buf.append("</tr></thead>");
        buf.append("<tbody>");
        for (T row : rows)
        {
            buf.append("<tr>");
            for (RaplaTableColumn<T> col : columPlugins)
            {
                buf.append("<td>");
                final String htmlValue = col.getHtmlValue(row);
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


    protected abstract String getCalendarBody() throws RaplaException;

    /** Comparator to be used, when no sorting option is defined */
    protected abstract Comparator<T> getFallbackComparator();


}