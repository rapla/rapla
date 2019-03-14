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

import org.rapla.components.util.TimeInterval;
import org.rapla.components.util.Tools;
import org.rapla.entities.User;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.facade.CalendarModel;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.TableViewPlugin;
import org.rapla.plugin.tableview.internal.TableConfig;
import org.rapla.server.PromiseWait;
import org.rapla.server.extensionpoints.HTMLViewPage;

import javax.inject.Inject;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.swing.table.TableColumn;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Extension(provides = HTMLViewPage.class, id = TableViewPlugin.TABLE_APPOINTMENTS_PER_DAY_VIEW) public class AppointmentPerDayViewPage
        implements HTMLViewPage
{
    private TableViewPage<AppointmentBlock, TableColumn> tableViewPage;
    RaplaLocale raplaLocale;
    String getDayString(AppointmentBlock block)
    {
        final Date start = new Date(block.getStart());
        return raplaLocale.formatDayOfWeekLongDateMonth(start);
    }

    @Inject public AppointmentPerDayViewPage(PromiseWait waiter,RaplaLocale raplaLocale, final TableConfig.TableConfigLoader tableConfigLoader)
    {
        this.raplaLocale = raplaLocale;
        tableViewPage = new TableViewPage<AppointmentBlock, TableColumn>(raplaLocale) {

            @Override
            public String getCalendarHTML() throws RaplaException
            {
                User user = model.getUser();
                final String tableViewName = TableConfig.APPOINTMENTS_PER_DAY_VIEW;
                List<RaplaTableColumn<AppointmentBlock, TableColumn>> columnPlugins = tableConfigLoader.loadColumns(tableViewName, user);
                final TimeInterval timeIntervall = model.getTimeIntervall();
                final List<AppointmentBlock> blocks = waiter.waitForWithRaplaException(model.queryBlocks(timeIntervall), 10000);
                final  Map<String,List<AppointmentBlock>> blockSorter = new LinkedHashMap<>();
                blocks.stream().forEach( block->
                {
                        String day =getDayString(block);
                        List<AppointmentBlock> appointmentBlocks = blockSorter.get(day);
                        if ( appointmentBlocks == null)
                        {
                            appointmentBlocks = new ArrayList<>();
                            blockSorter.put( day, appointmentBlocks);
                        }
                        appointmentBlocks.add( block );
                }
                );
                return getCalendarHTML(columnPlugins, blockSorter);
            }

            public String getCalendarHTML(List<RaplaTableColumn<AppointmentBlock, TableColumn>> columPlugins,Map<String,List<AppointmentBlock>> blocks)
            {
                List<TableRow> rows = new ArrayList<>();

                StringBuffer buf = new StringBuffer();
                buf.append("<div class=\"export table \">");
                buf.append("<div class=\"tr\">");
                for (RaplaTableColumn<?, ?> col : columPlugins)
                {
                    buf.append("<div class=\"th\">");
                    buf.append(Tools.createXssSafeString(col.getColumnName()));
                    buf.append("</div>");
                }
                buf.append("</div>");
                blocks.entrySet().stream().forEach( entry-> {
                            String title = entry.getKey();
                            //buf.append("<div style=\"clear:both;\"></div>");
                            buf.append("<div class=\"appointments_per_day\">");
                            buf.append(title);
                            buf.append("</div>");
                            for (AppointmentBlock row : entry.getValue())
                            {

                                buf.append("<div class=\"tr\">");
                                for (RaplaTableColumn<AppointmentBlock, ?> col : columPlugins)
                                {
                                    final String columnName = Tools.createXssSafeString(col.getColumnName());
                                    buf.append("<div class=\"td "+columnName +"\">");
                                    final String htmlValue = col.getHtmlValue(row);
                                    buf.append(htmlValue);
                                    buf.append("</div>");
                                }
                                buf.append("</div>");
                            }
                        }
                );
                buf.append("</div>");
                //buf.append("</table>");
                final String result = buf.toString();
                return result;
            }

            @Override
            public int compareTo(AppointmentBlock object1, AppointmentBlock object2)
            {
                return object1.compareTo(object2);
            }
            
        };
    }
    
    @Override
    public void generatePage( ServletContext context, HttpServletRequest request, HttpServletResponse response, CalendarModel model ) throws IOException, ServletException
    {
        tableViewPage.generatePage(context, request, response, model);
    }
    

}

