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
package org.rapla.plugin.autoexport.server;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.rapla.RaplaMainContainer;
import org.rapla.components.util.IOUtil;
import org.rapla.entities.User;
import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.plugin.autoexport.AutoExportPlugin;
import org.rapla.servletpages.RaplaPageGenerator;


public class CalendarListPageGenerator extends RaplaComponent implements RaplaPageGenerator
{
    public CalendarListPageGenerator( RaplaContext context ) 
    {
        super( context );
        setChildBundleName( AutoExportPlugin.AUTOEXPORT_PLUGIN_RESOURCE );
    }

    public void generatePage( ServletContext servletContext, HttpServletRequest request, HttpServletResponse response )
            throws IOException, ServletException
    {
        java.io.PrintWriter out = response.getWriter();
        try
        {
            String username = request.getParameter( "user" );
            response.setContentType("text/html; charset=" + getRaplaLocale().getCharsetNonUtf() );
            User[] users = getQuery().getUsers();
            SortedSet<User> sortedUsers = new TreeSet<User>( User.USER_COMPARATOR);
            sortedUsers.addAll( Arrays.asList( users));
            if ( username != null)
            {
                users = new User[] { getQuery().getUser( username )}; 
            }
			String calendarName = getQuery().getSystemPreferences().getEntryAsString(RaplaMainContainer.TITLE, getString("rapla.title"));
            out.println( "<html>" );
            out.println( "<head>" );
            out.println( "<title>" + calendarName + "</title>" );
            out.println( "</head>" );
            out.println( "<body>" );            
            
            out.println( "<h2>" + getString("webserver") + ": " + calendarName + "</h2>");            
          
            for (User user : sortedUsers)
            {
                Preferences preferences = getQuery().getPreferences( user );
                LinkedHashMap<String, CalendarModelConfiguration> completeMap = new LinkedHashMap<String, CalendarModelConfiguration>();
                CalendarModelConfiguration defaultConf =  preferences.getEntry( CalendarModelConfiguration.CONFIG_ENTRY );
                if ( defaultConf != null)
                {
                    completeMap.put( getString("default"), defaultConf);
                }
                
                final RaplaMap<CalendarModelConfiguration> raplaMap =  preferences.getEntry( AutoExportPlugin.PLUGIN_ENTRY );
                if ( raplaMap != null)
                {
                	for ( Map.Entry<String, CalendarModelConfiguration> entry: raplaMap.entrySet())
                	{
                		CalendarModelConfiguration value = entry.getValue();
						completeMap.put(entry.getKey(),value);
                	}
                }
                SortedMap<String,CalendarModelConfiguration> sortedMap = new TreeMap<String,CalendarModelConfiguration>( new TitleComparator( completeMap));
                sortedMap.putAll( completeMap);
                Iterator<Map.Entry<String, CalendarModelConfiguration>> it  = sortedMap.entrySet().iterator();
                
                int count =0;
                while ( it.hasNext())
                {
                    Map.Entry<String,CalendarModelConfiguration> entry = it.next();
                    String key =  entry.getKey();
                    CalendarModelConfiguration conf  = entry.getValue();
                    final Object isSet = conf.getOptionMap().get(AutoExportPlugin.HTML_EXPORT);
                    if( isSet != null && isSet.equals("false"))
                    {
                        it.remove();
                        continue;
                    }
                	if(count == 0) 
                	{
                        String userName = user.getName();
                        if(username == null || userName.trim().length() ==0)
                        	userName = user.getUsername();
                        out.println( "<h3>" + userName + "</h3>" ); //BJO            
                        out.println( "<ul>" );
                    }
                	count++;
                	String title = getTitle(key, conf);
                    
                    String filename =URLEncoder.encode( key, "UTF-8" );
                    out.print( "<li>" );
                    out.print( "<a href=\"?page=calendar&user=" + user.getUsername() + "&file=" + filename + "&details=*" + "&folder=true" + "\">");
                    out.print( title);
                    out.print( "</a>" );
                    out.println( "</li>" );
                }
                if (count > 0)
                {
                    out.println( "</ul>" );
                }
            }
            out.println( "</body>" );   
            out.println( "</html>" );
        }
        catch ( Exception ex )
        {
            out.println( IOUtil.getStackTraceAsString( ex ) );
            throw new ServletException( ex );
        }
        finally
        {
            out.close();
        }
    }

    private String getTitle(String key, CalendarModelConfiguration conf) {
        String title = conf.getTitle() ;
        if ( title == null || title.trim().length() == 0)
        {
            title = key;
        }
        return title;
    }

    class TitleComparator implements Comparator<String> {

        Map<String,CalendarModelConfiguration> base;
        
        public TitleComparator(Map<String,CalendarModelConfiguration> base) {
            this.base = base;
        }

        public int compare(String a, String b) {

          final String title1 = getTitle(a,base.get(a));
          final String title2 = getTitle(b,base.get(b));
          int result = title1.compareToIgnoreCase( title2);
          if ( result != 0)
          {
              return result;
          }
          return a.compareToIgnoreCase( b);
          
        }
      }

}

