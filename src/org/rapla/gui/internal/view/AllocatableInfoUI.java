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
package org.rapla.gui.internal.view;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.rapla.components.util.TimeInterval;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.framework.RaplaContext;

public class AllocatableInfoUI extends ClassificationInfoUI<Allocatable> {
    public AllocatableInfoUI(RaplaContext sm) {
        super(sm);
    }

    void insertPermissions( Allocatable allocatable, StringBuffer buf ) {
        User user;
        Date today;
        try {
            user = getUser();
            today = getQuery().today();
        } catch (Exception ex) {
            return;
        }
        TimeInterval accessInterval = allocatable.getAllocateInterval( user , today);
        if ( accessInterval != null)
        {
	        buf.append( "<strong>" );
	        buf.append( getString( "allocatable_in_timeframe" ) );
	        buf.append( ":</strong>" );
	        buf.append("<br>");
	
	        Date start = accessInterval.getStart();
			Date end = accessInterval.getEnd();
			if ( start == null && end == null ) {
	            buf.append( getString("everytime") );
	        }
			else
			{
				if ( start != null ) {
                    buf.append( getRaplaLocale().formatDate( start ) );
                } else {
                    buf.append(getString("open"));
                }
                buf.append(" - ");
                if ( end != null ) {
                    buf.append( getRaplaLocale().formatDate( end ) );
                } else {
                    buf.append(getString("open"));
                }
                buf.append("<br>");
			}
        }
    }
    
    @Override
    protected String createHTMLAndFillLinks(Allocatable allocatable,LinkController controller) {
        StringBuffer buf = new StringBuffer();
        insertModificationRow( allocatable, buf );
        insertClassificationTitle( allocatable, buf );
        createTable( getAttributes( allocatable, controller, false),buf,false);
        return buf.toString();
    }
    
    public List<Row> getAttributes(Allocatable allocatable,LinkController controller,  boolean excludeAdditionalInfos) {
        ArrayList<Row> att = new ArrayList<Row>();
        att.addAll( super.getClassificationAttributes( allocatable, excludeAdditionalInfos, controller ));
        final Locale locale = getLocale();
        User owner = allocatable.getOwner();
        User lastChangeBy = allocatable.getLastChangedBy();
        if ( owner != null)
        {
            final String ownerName = owner.getName(locale);
            String ownerText = encode(ownerName);
            if (controller != null)
                ownerText = controller.createLink(owner,ownerName);
            
            att.add( new Row(getString("resource.owner"), ownerText));
        }
        if ( lastChangeBy != null && (owner == null || !lastChangeBy.equals(owner))) {
            final String lastChangedName = lastChangeBy.getName(locale);
            String lastChangeByText = encode(lastChangedName);
            if (controller != null)
                lastChangeByText = controller.createLink(lastChangeBy,lastChangedName);
            att.add( new Row(getString("last_changed_by"), lastChangeByText));
            
        }
       
        return att;
    }

    @Override
    public String getTooltip(Allocatable allocatable) {
        StringBuffer buf = new StringBuffer();
        insertClassificationTitle( allocatable, buf );
        insertModificationRow( allocatable, buf );
        Collection<Row> att = new ArrayList<Row>();
        att.addAll(getAttributes(allocatable,  null,  true));
        createTable(att,buf);
        insertPermissions( allocatable, buf );
        return buf.toString();
    }



}

