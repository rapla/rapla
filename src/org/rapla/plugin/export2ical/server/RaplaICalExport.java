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
package org.rapla.plugin.export2ical.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.TimeZone;

import org.rapla.entities.User;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.export2ical.ICalExport;
import org.rapla.server.RemoteMethodFactory;
import org.rapla.server.RemoteSession;
import org.rapla.server.TimeZoneConverter;
import org.rapla.storage.RaplaSecurityException;

import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.ValidationException;

public class RaplaICalExport extends RaplaComponent implements RemoteMethodFactory<ICalExport>
{
    
    public RaplaICalExport( RaplaContext context) {
        super( context );
    }

    
    private void export(String[] appointmentIds, OutputStream out, User user ) throws RaplaException, IOException 
    {
        TimeZone timeZone = getContext().lookup( TimeZoneConverter.class).getImportExportTimeZone();
        
		Export2iCalConverter converter = new Export2iCalConverter(getContext(),timeZone, null);
        if ( appointmentIds.length == 0)
        {
            return;
        }
        EntityResolver operator = (EntityResolver) getClientFacade().getOperator();
        Collection<Appointment> appointments = new ArrayList<Appointment>();
        for ( String id:appointmentIds)
        {
        	Appointment app = operator.resolve( id , Appointment.class);
        	appointments.add( app );
        }
        Calendar iCal = converter.createiCalender(appointments, user);
        if (null != iCal) {
            export(iCal, out);
        }
    }
    
    private void export(Calendar ical, OutputStream out) throws RaplaException,IOException
    {
            CalendarOutputter calOutputter = new CalendarOutputter();
            try {
                calOutputter.output(ical, out);
            } catch (ValidationException e) {
                throw new RaplaException(e.getMessage());
            }
       
    }
    
	private String export_( String[] appointmentIds, User user) throws RaplaException
    {
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	try {
			export(appointmentIds, out, user);
		} catch (IOException e) {
			throw new RaplaException( e.getMessage() , e);
		}
    	return out.toString();
    }

    public ICalExport createService(final RemoteSession remoteSession) throws RaplaContextException {
        if ( !remoteSession.isAuthentified())
        {
            RaplaSecurityException ex = new RaplaSecurityException("User not authentified for ical export");
            throw new RaplaContextException(ex.getMessage(), ex);
        }
        return new ICalExport()
                {
                    @Override
                    public String export(String[] appointments) throws RaplaException
                    {
                        return export_(appointments, remoteSession.getUser());
                    }
            
                }
        ;
    }
}

