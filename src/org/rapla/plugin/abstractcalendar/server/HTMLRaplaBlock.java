/*--------------------------------------------------------------------------*
 | Copyright (C) 2006  Christopher Kohlhaas                                 |
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


import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import org.rapla.components.calendarview.html.HTMLBlock;
import org.rapla.components.util.xml.XMLWriter;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.gui.internal.view.AppointmentInfoUI;
import org.rapla.plugin.abstractcalendar.AbstractRaplaBlock;


public class HTMLRaplaBlock extends AbstractRaplaBlock implements HTMLBlock {
    private int m_day;
    private int m_row;
    private int m_rowCount;
    private int index = 0;
    {
    	timeStringSeperator ="&#160;-";
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public void setRowCount(int rows) {
        m_rowCount = rows;
    }

    public void setRow(int row) {
        m_row = row;
    }

    public int getRowCount() {
        return m_rowCount;
    }

    public int getRow() {
        return m_row;
    }

    public void setDay(int day) {
        m_day = day;
    }

    public int getDay() {
        return m_day;
    }

    public String getBackgroundColor() {
        return getColorsAsHex()[0];
    }

    
    public String toString() {
        StringBuffer buf = new StringBuffer();
        String label = XMLWriter.encode(getReservationName( ));
        String timeString = getTimeString(false);

        if ( getContext().isAnonymous()) {
            String anonymous = "&#160;&#160;&#160;&#160;???";
            if ( timeString != null) {
                return timeString + " " + anonymous;
            } else {
                return anonymous;
            }
        }

        if ( timeString != null) {
        	
        	      List<Allocatable> resources = getContext().getAllocatables();
                  StringBuffer buffer  = new StringBuffer() ;
                  for (Allocatable resource: resources)
                  {
                  	if ( getContext().isVisible( resource) && !resource.isPerson())
                  	{
                  		if ( buffer.length() > 0)
                  		{
                  			buffer.append(", ");
                  		}
                  		buffer.append(XMLWriter.encode( getResourceName(resource)));
                  	}
                  }
                  if (  !getBuildContext().isResourceVisible() && buffer.length() > 0)
                  {
                  	timeString = timeString + " " + buffer.toString();
                  }
              
        	label = timeString + "<br/>" + label;
        }
        
        
        
        AppointmentInfoUI reservationInfo = new AppointmentInfoUI(getContext().getBuildContext().getServiceManager());
        URL url = null;
        Attribute[] attributes = getReservation().getClassification().getAttributes();
        for ( int i=0;i<attributes.length;i++) {
            String value = getReservation().getClassification().getValueAsString( attributes[i],getBuildContext().getRaplaLocale().getLocale());
            if ( value == null)
                continue;
            try{
            	int httpEnd = Math.max( value.indexOf(" ")-1, value.length());
            	url = new URL( value.substring(0,httpEnd));
            	break;
            } 
            catch (MalformedURLException ex)
            {
            	
            }
        }
            buf.append( "<a href=\"");
            if ( url != null) {
                buf.append( url );
            } else {
                buf.append( "#" + index );
            }
            buf.append( "\">" );
            if ( url != null) {
                buf.append( "<span class=\"link\">");
            }
            buf.append( label  );
            if ( url != null) {
                buf.append( "</span>");
            }
        if (getBuildContext().isShowToolTips())
        {

            buf.append( "<span class=\"tooltip\">");
            buf.append(reservationInfo.getTooltip(getAppointment()));
            buf.append( "</span>");
        }

        buf.append( "</a>" );


        if  (getBuildContext().isPersonVisible()) {
        	List<Allocatable> persons = getContext().getAllocatables();
               
            for ( Allocatable person:persons)
            {
            	if ( !getContext().isVisible( person) || !person.isPerson())
              	  continue;
            	buf.append("<br>");
                buf.append("<span class=\"person\">");
                buf.append(XMLWriter.encode(getResourceName(person)));
                buf.append("</span>");
            }
        }
        else
        {
        	   List<Allocatable> persons = getContext().getAllocatables();
        	   if ( persons.size() > 0)
        	   {
        		   buf.append("<br>");   
   	        	   buf.append("<span class=\"person\">");
	        	   boolean first = true;
	               for ( Allocatable person:persons)
	               {
	            	   if ( !getContext().isVisible( person) || !person.isPerson())
	                 	  continue;
	                 
	            	   if ( !first)
	                   {
	                	   buf.append(", ");
	                   }
	            	   else
	            	   {
	            		   first  = false;
	            	   }
	                   buf.append( XMLWriter.encode(getResourceName( person )));
	               }
	               buf.append("</span>");
        	   }
        }
        if  (getBuildContext().isResourceVisible()) {
            Allocatable[] resources = getReservation().getResources();
            for (int i=0; i<resources.length;i ++) {
                if (!getContext().isVisible(resources[i]))
                    continue;
                buf.append("<br>");
                buf.append("<span class=\"resource\">");
                buf.append(XMLWriter.encode(getResourceName(resources[i])));
                buf.append("</span>");
            }
        }
        return buf.toString();
    }
}
