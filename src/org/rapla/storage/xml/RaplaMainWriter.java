/*--------------------------------------------------------------------------*
  |                                                                          |
  | This program is free software; you can redistribute it and/or modify     |
  | it under the terms of the GNU Genseral Public License as published by the |
  | Free Software Foundation. A copy of the license has been included with   |
  | these distribution in the COPYING file, if not go to www.fsf.org         |
  |                                                                          |
  | As a special exception, you are granted the permissions to link this     |
  | program with every library, which license fulfills the Open Source       |
  | Definition as published by the Open Source Initiative (OSI).             |
  *--------------------------------------------------------------------------*/
package org.rapla.storage.xml;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.rapla.components.util.Assert;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.facade.Conflict;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.storage.LocalCache;

/** Stores the data from the local cache in XML-format to a print-writer.*/
public class RaplaMainWriter extends RaplaXMLWriter
{
    protected final static String OUTPUT_FILE_VERSION="1.2";
	String encoding = "utf-8";
    protected LocalCache cache;
    private String version = OUTPUT_FILE_VERSION;

    public RaplaMainWriter(RaplaContext context, LocalCache cache) throws RaplaException {
        super(context);
        this.cache = cache;
        Assert.notNull(cache);
    }    

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setWriter( Appendable writer ) {
        super.setWriter( writer );
        for ( RaplaXMLWriter xmlWriter: writerMap.values()) {
            xmlWriter.setWriter( writer );
        }
    }
    
    
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }
    
    public void printContent() throws IOException,RaplaException {
        printHeader( 0, null, false );

        printCategories();
        println();
        printDynamicTypes();
        println();
        ((PreferenceWriter)getWriterFor(Preferences.TYPE)).printPreferences( cache.getPreferencesForUserId( null ));
        println();
        printUsers();
        println();
        printAllocatables();
        println();
        printReservations();
        println();
        Collection<Conflict> conflicts = cache.getConflicts();
        if ( conflicts.size() > 0)
        {
            printDisabledConflicts(conflicts);
            println();
        }
        closeElement("rapla:data");
    }
    
    public void printDynamicTypes()  throws IOException,RaplaException {
        openElement("relax:grammar");
    	DynamicTypeWriter dynamicTypeWriter = (DynamicTypeWriter)getWriterFor(DynamicType.TYPE);
        for(  DynamicType type:cache.getDynamicTypes()) {
        	if ((( DynamicTypeImpl) type).isInternal())
        	{
        		continue;
        	}
			dynamicTypeWriter.printDynamicType(type);
            println();
        }
        printStartPattern();
        closeElement("relax:grammar");
    }
    
    private void printStartPattern() throws IOException {
        openElement("relax:start");
        openElement("relax:choice");
        for(  DynamicType type:cache.getDynamicTypes()) 
        {
        	if ((( DynamicTypeImpl) type).isInternal())
        	{
        		continue;
        	}
            openTag("relax:ref");
            att("name",type.getKey());
            closeElementTag();
        }
        closeElement("relax:choice");
        closeElement("relax:start");
    }

    
    public void printCategories() throws IOException,RaplaException {
        openElement("rapla:categories");
        
        CategoryWriter categoryWriter = (CategoryWriter)getWriterFor(Category.TYPE);
		Category[] categories = cache.getSuperCategory().getCategories();
    	for (int i=0;i<categories.length;i++) {
			categoryWriter.printCategory(categories[i]);
		}
        
        closeElement("rapla:categories");
    }
    public void printUsers()  throws IOException,RaplaException {
    	openElement("rapla:users");
        UserWriter userWriter = (UserWriter)getWriterFor(User.TYPE);
        println("<!-- Users of the system -->");
        for (User user:cache.getUsers()) {
        	String userId = user.getId();
			PreferencesImpl preferences = cache.getPreferencesForUserId( userId);
			String password = cache.getPassword(userId);
			userWriter.printUser( user, password, preferences);
        }
        closeElement("rapla:users");
    }

    
    public void printAllocatables() throws IOException,RaplaException {
        openElement("rapla:resources");
        println("<!-- resources -->");
        // Print all resources that are not persons
        AllocatableWriter allocatableWriter = (AllocatableWriter)getWriterFor(Allocatable.TYPE);
        Collection<Allocatable> allAllocatables = cache.getAllocatables();
        Map<String,List<Allocatable>> map = new LinkedHashMap<String,List<Allocatable>>();
        
		for (DynamicType type:cache.getDynamicTypes()) {
			map.put( type.getId(), new ArrayList<Allocatable>());
        }
		for (Allocatable allocatable:allAllocatables) {
            Classification classification = allocatable.getClassification();
			String id = classification.getType().getId();
			List<Allocatable> list = map.get( id);
			list.add( allocatable);
        }

        // Print all Persons
		for ( String id : map.keySet())
		{
	        List<Allocatable> list = map.get( id);
			for (Allocatable allocatable:list) {
	            allocatableWriter.printAllocatable(allocatable);
	        }
		}
        println();
        closeElement("rapla:resources");

    }
        
    void printReservations() throws IOException, RaplaException {
        openElement("rapla:reservations");
    	ReservationWriter reservationWriter = (ReservationWriter)getWriterFor(Reservation.TYPE);
        for (Reservation reservation: cache.getReservations()) {
			reservationWriter.printReservation( reservation );
        }
        closeElement("rapla:reservations");
    }

    void printDisabledConflicts(Collection<Conflict> conflicts) throws IOException {
        openElement("rapla:conflicts");
        for (Conflict conflict: conflicts) 
        {
            boolean enabledAppointment1 = conflict.isAppointment1Enabled();
            boolean enabledAppointment2 = conflict.isAppointment2Enabled();
            if ( enabledAppointment1 && enabledAppointment2)
            {
                continue;
            }
            openTag("rapla:conflict");
            att("resource", conflict.getAllocatableId());
            att("appointment1", conflict.getAppointment1());
            att("appointment2", conflict.getAppointment2());
            att("appointment1enabled", ""+enabledAppointment1);
            att("appointment2enabled", ""+ enabledAppointment2);
            closeElementTag();
        }
        closeElement("rapla:conflicts");
    }

    
    private void printHeader(long repositoryVersion, TimeInterval invalidateInterval, boolean resourcesRefresh) throws IOException
    {
        println("<?xml version=\"1.0\" encoding=\"" + encoding + "\"?><!--*- coding: " + encoding + " -*-->");
        openTag("rapla:data");
        for (int i=0;i<NAMESPACE_ARRAY.length;i++) {
            String prefix = NAMESPACE_ARRAY[i][1];
            String uri = NAMESPACE_ARRAY[i][0];
            if ( prefix == null) {
                att("xmlns", uri);
            } else {
                att("xmlns:" + prefix, uri);
            }
            println();
        }
        att("version", version);
        if ( repositoryVersion > 0)
        {
            att("repositoryVersion", String.valueOf(repositoryVersion));
        }
        if ( resourcesRefresh)
        {
            att("resourcesRefresh", "true");
        }
        if ( invalidateInterval != null)
        {
            Date startDate = invalidateInterval.getStart();
            Date endDate = invalidateInterval.getEnd(); 
			String start;
			if ( startDate == null)
			{
				startDate = new Date(0);
			}
			start = dateTimeFormat.formatDate( startDate);
			att("startDate", start);
        	if ( endDate != null)
        	{
        		String end = dateTimeFormat.formatDate( endDate);
            	att("endDate", end);
        	}
        }
        closeTag();
    }

}



