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

package org.rapla.plugin.abstractcalendar;

import org.rapla.components.calendarview.html.HTMLBlock;
import org.rapla.components.util.Tools;
import org.rapla.components.util.xml.XMLWriter;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.NameFormatUtil;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.internal.EvalContext;
import org.rapla.plugin.abstractcalendar.RaplaBuilder.RaplaBlockContext;

import java.util.Date;
import java.util.List;


public class HTMLRaplaBlock extends RaplaBlock implements HTMLBlock {
    private int m_day;
    private int m_row;
    private int m_rowCount;
    private int index = 0;
    EvalContext context;
    public HTMLRaplaBlock( RaplaBlockContext context, Date start, Date end)
    {
        super( context, start, end);
    	timeStringSeperator ="&#160;-";

    }

    @Override
    public String getReservationName()
    {
        return NameFormatUtil.getExportName(getAppointmentBlock(), getI18n().getLocale());
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
        String label = XMLWriter.encode(getReservationName());
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
                        final String allocatableName = getAllocatableName(resource);
                        if ( isEmpty( allocatableName))
                        {
                            continue;
                        }
                        if ( buffer.length() > 0)
                  		{
                  			buffer.append(", ");
                  		}
                        buffer.append(XMLWriter.encode(allocatableName));
                  	}
                  }
                  if (  !getBuildContext().isResourceVisible() && buffer.length() > 0)
                  {
                  	timeString = timeString + " " + buffer;
                  }
              
        	label = timeString + "<br/>" + label;
        }
        
        
        
        String url = null;
        Attribute[] attributes = getReservation().getClassification().getAttributes();
        for ( int i=0;i<attributes.length;i++) {
            String value = getReservation().getClassification().getValueAsString( attributes[i],getBuildContext().getRaplaLocale().getLocale());
            if ( value == null)
                continue;
            url = Tools.getUrl(value);
            if ( url != null) {
                break;
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
        if (getBuildContext().isShowTooltipsInHtmlExport() && getBuildContext().isShowToolTips() && !getContext().isAnonymous())
        {
            buf.append( "<span class=\"tooltip\">");
            buf.append(getContext().getTooltip());
            buf.append( "</span>");
        }

        buf.append( "</a>" );


        if  (getBuildContext().isPersonVisible()) {
        	List<Allocatable> persons = getContext().getAllocatables();
               
            for ( Allocatable person:persons)
            {
            	if ( !getContext().isVisible( person) || !person.isPerson())
              	  continue;
                final String allocatableName = getAllocatableName(person);
                if ( isEmpty( allocatableName))
                {
                    continue;
                }
                buf.append("<br>");
                buf.append("<span class=\"person\">");
                buf.append(XMLWriter.encode(allocatableName));
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
                       final String allocatableName = getAllocatableName(person);
                       if ( isEmpty( allocatableName))
                       {
                           continue;
                       }
                       if ( !first)
	                   {
	                	   buf.append(", ");
	                   }
	            	   else
	            	   {
	            		   first  = false;
	            	   }
                       buf.append( XMLWriter.encode(allocatableName));
	               }
	               buf.append("</span>");
        	   }
        }
        if  (getBuildContext().isResourceVisible()) {
            Allocatable[] resources = getReservation().getResources();
            for (int i=0; i<resources.length;i ++) {
                if (!getContext().isVisible(resources[i]))
                    continue;
                final String allocatableName = getAllocatableName(resources[i]);
                if ( isEmpty( allocatableName))
                {
                    continue;
                }
                buf.append("<br>");
                buf.append("<span class=\"resource\">");
                buf.append(XMLWriter.encode(allocatableName));
                buf.append("</span>");
            }
        }
        return buf.toString();
    }

    private boolean isEmpty(String allocatableName)
    {
        return allocatableName == null || allocatableName.trim().isEmpty();
    }

    @Override
    public RaplaBlockContext getContext()
    {
        return super.getContext();
    }

    protected String getAllocatableName(Allocatable allocatable)
    {
        final String exportName = NameFormatUtil.getExportName(allocatable, m_raplaLocale.getLocale());
        return exportName;
    }
}
