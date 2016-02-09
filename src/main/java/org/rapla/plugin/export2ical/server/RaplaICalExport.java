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

import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.ValidationException;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.plugin.export2ical.ICalExport;
import org.rapla.server.RemoteSession;
import org.rapla.storage.RaplaSecurityException;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

@DefaultImplementation(of = ICalExport.class, context = InjectionContext.server)
public class RaplaICalExport implements ICalExport
{
    RaplaFacade facade;
    RemoteSession session;
    Export2iCalConverter iCalConverter;

    @Inject
    public RaplaICalExport(  RaplaFacade facade, RemoteSession session, Export2iCalConverter iCalConverter)
    {
        this.facade = facade;
        this.session = session;
        this.iCalConverter = iCalConverter;
    }

    public void export(User user,Set<String> appointmentIds, OutputStream out ) throws RaplaException, IOException
    {
        if ( appointmentIds.size() == 0)
        {
            return;
        }
        EntityResolver operator = facade.getOperator();
        Collection<Appointment> appointments = new ArrayList<Appointment>();
        for ( String id:appointmentIds)
        {
        	Appointment app = operator.resolve(id, Appointment.class);
            boolean canRead = facade.getPermissionController().canRead(app, user);
            if (canRead)
            {
                appointments.add(app);
            }

        }
        Preferences preferences =facade.getPreferences( user);
        Calendar iCal = iCalConverter.createiCalender(appointments, preferences, user);
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
    
    @Override
	public String export( Set<String> appointmentIds) throws RaplaException
    {
        if (!session.isAuthentified())
        {
            throw new RaplaSecurityException("Not authentified");
        }
        User user = session.getUser();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
    	try {
			export(user,appointmentIds, out);
		} catch (IOException e) {
			throw new RaplaException( e.getMessage() , e);
		}
    	return out.toString();
    }

    public ICalExport createService(RemoteSession remoteSession) {
        return RaplaICalExport.this;
    }
}

