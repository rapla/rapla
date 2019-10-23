package org.rapla.plugin.tableview.server;

import org.rapla.components.util.IOUtil;
import org.rapla.components.util.Tools;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.CalendarModel;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.plugin.abstractcalendar.server.AbstractHTMLCalendarPage;
import org.rapla.plugin.tableview.RaplaTableColumn;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

abstract public class TableViewPage<T, C>
{

    protected CalendarModel model;

    private final RaplaLocale raplaLocale;
    private boolean csv;

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
                AbstractHTMLCalendarPage.printAllocatableList(request, out, raplaLocale.getLocale(), selectedAllocatables, true);
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

    class TableRow implements Comparable<TableRow>
    {
        T object;
        @SuppressWarnings("rawtypes")
        Map<RaplaTableColumn, Integer> sortDirections;

        TableRow(T originalObject, Map<RaplaTableColumn, Integer> sortDirections)
        {
            this.object = originalObject;
            this.sortDirections = sortDirections;
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        public int compareTo(TableRow o)
        {
            if (o.equals(this))
            {
                return 0;
            }
            for (Map.Entry<RaplaTableColumn, Integer> entry : sortDirections.entrySet())
            {
                RaplaTableColumn column = entry.getKey();
                int direction = entry.getValue();
                Object v1 = column.getValue(object);
                Object v2 = column.getValue(o.object);
                if (v1 != null && v2 != null)
                {
                    Class<?> columnClass = column.getColumnClass();
                    if (columnClass.equals(String.class))
                    {
                        return String.CASE_INSENSITIVE_ORDER.compare(v1.toString(), v2.toString()) * direction;
                    }
                    else if (columnClass.isAssignableFrom(Comparable.class))
                    {
                        return ((Comparable) v1).compareTo(v2) * direction;
                    }
                }
            }
            T object1 = object;
            T object2 = o.object;
            return TableViewPage.this.compareTo(object1, object2);
        }
    }

    public String getCalendarBody(List<RaplaTableColumn<T, C>> columPlugins, Collection<T> rowObjects, Map<RaplaTableColumn, Integer> sortDirections) {
        List<TableRow> rows = new ArrayList<>();
        for (T r : rowObjects)
        {
            rows.add(new TableRow(r, sortDirections));
        }
        Collections.sort(rows);
        if (isCsv())
        {
            return  getCalendarBodyCSV(columPlugins, rows);
        }
        else
        {
            return  getCalendarBodyHTML(columPlugins, rows);
        }
    }

    public String getCalendarBodyHTML(List<RaplaTableColumn<T, C>> columPlugins, List<TableRow> rows)
    {

        StringBuffer buf = new StringBuffer();
        buf.append("<table class='export table table-striped table-bordered' style='width: 99%; margin: 0 auto;'>");
        buf.append("<thead><tr>");
        for (RaplaTableColumn<?, C> col : columPlugins)
        {
            buf.append("<th>");
            buf.append(col.getColumnName());
            buf.append("</th>");
        }
        buf.append("</tr></thead>");
        buf.append("<tbody>");
        for (TableRow row : rows)
        {
            buf.append("<tr>");
            for (RaplaTableColumn<T, C> col : columPlugins)
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

    private static final String LINE_BREAK = "\n";
    private static final String CELL_BREAK = ";";
    public String getCalendarBodyCSV(List<RaplaTableColumn<T, C>> columns, List<TableRow> rows)
    {
        StringBuffer buf = new StringBuffer();
        for (RaplaTableColumn column : columns)
        {
            buf.append(column.getColumnName());
            buf.append(CELL_BREAK);
        }
        for (TableRow row : rows)
        {
            buf.append(LINE_BREAK);
            for (RaplaTableColumn column : columns)
            {
                T rowObject = row.object;
                Object value = column.getValue(rowObject);
                Class columnClass = column.getColumnClass();
                boolean isDate = columnClass.isAssignableFrom(java.util.Date.class);
                String formated = "";
                if (value != null)
                {
                    if (isDate)
                    {
                        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        format.setTimeZone(IOUtil.getTimeZone());
                        String timestamp = format.format((java.util.Date) value);
                        formated = timestamp;
                    }
                    else
                    {
                        String escaped = escape(value);
                        formated = escaped;
                    }
                }
                buf.append(formated);
                buf.append(CELL_BREAK);
            }
        }
        final String result = buf.toString();
        return result;
    }

    private String escape(Object cell) {
        return cell.toString().replace(LINE_BREAK, " ").replace(CELL_BREAK, " ");
    }

    abstract String getCalendarBody() throws RaplaException;

    abstract int compareTo(T object1, T object2);


}