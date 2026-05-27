/*--------------------------------------------------------------------------*
 | Copyright (C) 2012 Christopher Kohlhaas                                  |
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
package org.rapla.plugin.tableview.server;

import org.rapla.components.i18n.I18nBundle;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.components.util.Tools;
import org.rapla.entities.User;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.facade.CalendarModel;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.RaplaTableModel;
import org.rapla.plugin.tableview.TableViewPlugin;
import org.rapla.plugin.tableview.internal.DefaultRaplaTableColumn;
import org.rapla.plugin.tableview.internal.TableConfig;
import org.rapla.server.extensionpoints.HTMLViewPage;

import org.springframework.beans.factory.annotation.Autowired;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

import java.time.LocalDateTime;
 public class AppointmentPerDayViewPage
        implements HTMLViewPage
{
    private final TableViewPage<AppointmentBlock> tableViewPage;
    RaplaLocale raplaLocale;
    String getDayString(AppointmentBlock block)
    {
        final LocalDateTime start = DateTools.toLocalDateTime(block.getStart());
        return raplaLocale.formatDayOfWeekLongDateMonth(start);
    }

    @Autowired public AppointmentPerDayViewPage(RaplaLocale raplaLocale, final TableConfig.TableConfigLoader tableConfigLoader)
    {
        this.raplaLocale = raplaLocale;
        tableViewPage = new TableViewPage<AppointmentBlock>(raplaLocale) {

            @Override
            public String getCalendarBody() throws RaplaException
            {
                User user = model.getUser();
                final String tableViewName = TableConfig.APPOINTMENTS_PER_DAY_VIEW;
                List<RaplaTableColumn<AppointmentBlock>> columnPlugins = tableConfigLoader.loadColumns(tableViewName, user);

                final TimeInterval timeIntervall = model.getTimeIntervall();
                final List<AppointmentBlock> blocks = ((org.rapla.facade.SyncCalendarModel) model).queryBlocksSync(timeIntervall);
                final  Map<String,List<AppointmentBlock>> blockSorter = new LinkedHashMap<>();
                if (isCsv()) {
                    // loadColumns(APPOINTMENTS_PER_DAY_VIEW) already includes
                    // the leading "date" column.
                    Map<RaplaTableColumn<AppointmentBlock>, Integer> sortDirections = RaplaTableModel.getSortDirections(model,columnPlugins, tableViewName);
                    return super.getCalendarBody( columnPlugins, blocks, sortDirections);
                }
                else
                {
                    blocks.stream().forEach(block -> {
                        String day = getDayString(block);
                        List<AppointmentBlock> appointmentBlocks = blockSorter.get(day);
                        if (appointmentBlocks == null)
                        {
                            appointmentBlocks = new ArrayList<>();
                            blockSorter.put(day, appointmentBlocks);
                        }
                        appointmentBlocks.add(block);
                    });
                    return getCalendarBodyHTML(columnPlugins, blockSorter);
                }
            }

            public String getCalendarBodyHTML(List<RaplaTableColumn<AppointmentBlock>> columPlugins,Map<String,List<AppointmentBlock>> blocks)
            {
                StringBuffer buf = new StringBuffer();
                {
                    buf.append("<div class=\"export table \">");
                    buf.append("<div class=\"tr\">");
                    for (RaplaTableColumn<?> col : columPlugins)
                    {
                        buf.append("<div class=\"th\">");
                        buf.append(Tools.createXssSafeString(col.getColumnName()));
                        buf.append("</div>");
                    }
                    buf.append("</div>");
                    blocks.entrySet().stream().forEach(entry -> {
                        String title = entry.getKey();
                        //buf.append("<div style=\"clear:both;\"></div>");
                        buf.append("<div class=\"appointments_per_day\">");
                        buf.append(title);
                        buf.append("</div>");
                        for (AppointmentBlock row : entry.getValue())
                        {

                            buf.append("<div class=\"tr\">");
                            for (RaplaTableColumn<AppointmentBlock> col : columPlugins)
                            {
                                final String columnName = Tools.createXssSafeString(col.getColumnName());
                                buf.append("<div class=\"td " + columnName + "\">");
                                final String htmlValue = col.getHtmlValue(row);
                                buf.append(htmlValue);
                                buf.append("</div>");
                            }
                            buf.append("</div>");
                        }
                    });
                }
                buf.append("</div>");
                //buf.append("</table>");
                final String result = buf.toString();
                return result;
            }

            @Override
            protected Comparator<AppointmentBlock> getFallbackComparator() {
                return Comparator.naturalOrder();
            }

        };
    }
    
    @Override
    public void generatePage( ServletContext context, HttpServletRequest request, HttpServletResponse response, CalendarModel model ) throws IOException, ServletException
    {
        tableViewPage.generatePage(context, request, response, model);
    }
    

}

