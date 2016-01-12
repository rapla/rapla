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
package org.rapla.client.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.rapla.RaplaResources;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;

public class AllocatableInfoUI extends ClassificationInfoUI<Allocatable> {
    public AllocatableInfoUI(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger) {
        super(i18n, raplaLocale, facade, logger);
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
    public String createHTMLAndFillLinks(Allocatable allocatable,LinkController controller) {
        StringBuffer buf = new StringBuffer();
        insertModificationRow( allocatable, buf );
        insertClassificationTitle( allocatable, buf );
        final User user = getClientFacade().getUser();
        createTable( getAttributes( allocatable, controller, false, user),buf,false);
        return buf.toString();
    }
    
    public List<Row> getAttributes(Allocatable allocatable,LinkController controller,  boolean excludeAdditionalInfos, User user) {
        ArrayList<Row> att = new ArrayList<Row>();
        att.addAll( super.getClassificationAttributes( allocatable, excludeAdditionalInfos, controller, user) );
        String ownerId = allocatable.getOwnerId();
        String lastChangeById = allocatable.getLastChangedBy();
        if ( ownerId != null)
        {
            final String ownerName = getUsername(ownerId);
            String ownerText = encode(ownerName);
            att.add( new Row(getString("resource.owner"), ownerText));
        }
        if ( lastChangeById != null && (ownerId == null || !lastChangeById.equals(ownerId))) {
            final String lastChangedName = getUsername(lastChangeById);
            String lastChangeByText = encode(lastChangedName);
            att.add( new Row(getString("last_changed_by"), lastChangeByText));
            
        }
       
        return att;
    }

    @Override
    public String getTooltip(Allocatable allocatable, User user) {
        StringBuffer buf = new StringBuffer();
        insertClassificationTitle( allocatable, buf );
        insertModificationRow( allocatable, buf );
        Collection<Row> att = new ArrayList<Row>();
        att.addAll(getAttributes(allocatable,  null,  true, user));
        createTable(att,buf);
        insertPermissions( allocatable, buf );
        return buf.toString();
    }



}

