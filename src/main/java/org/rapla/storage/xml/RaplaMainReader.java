/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org .       |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/

package org.rapla.storage.xml;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.components.util.xml.RaplaSAXAttributes;
import org.rapla.components.util.xml.RaplaSAXParseException;
import org.rapla.entities.Category;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.ImportExportEntity;
import org.rapla.facade.Conflict;
import org.rapla.framework.RaplaException;

public class RaplaMainReader extends RaplaXMLReader
{
    private Map<String,RaplaXMLReader> localnameTable = new HashMap<String,RaplaXMLReader>();
    public final static String INPUT_FILE_VERSION = RaplaMainWriter.OUTPUT_FILE_VERSION;
    private TimeInterval invalidateInterval = null;
    private boolean resourcesRefresh = false;
    RaplaDefaultXMLContext writeableContext;
  
	public RaplaMainReader( RaplaDefaultXMLContext context ) throws RaplaException
    {
        super( context );
        writeableContext = context;
        Map<Class<? extends RaplaObject>,RaplaXMLReader> readerMap = context.lookup( PreferenceReader.READERMAP );
        // Setup the delegation classes
        localnameTable.put( "grammar", readerMap.get( DynamicType.class ) );
        localnameTable.put( "element", readerMap.get( DynamicType.class ) );
        localnameTable.put( "user", readerMap.get( User.class ) );
        localnameTable.put( "category", readerMap.get( Category.class ) );
        localnameTable.put( "preferences", readerMap.get( Preferences.class ) );
        localnameTable.put( "resource", readerMap.get( Allocatable.class ) );
        localnameTable.put( "person", readerMap.get( Allocatable.class ) );
        localnameTable.put( "extension", readerMap.get( Allocatable.class ) );
        localnameTable.put( "period", readerMap.get( Period.class ) );
        localnameTable.put( "reservation", readerMap.get( Reservation.class ) );
        localnameTable.put( "conflict", readerMap.get( Conflict.class ) );
        localnameTable.put( "importexports", readerMap.get( ImportExportEntity.class ) );
        addChildHandler( readerMap.values() );
    }

    private void addChildHandler( Collection<? extends DelegationHandler> collection )
    {
        Iterator<? extends DelegationHandler> it = collection.iterator();
        while (it.hasNext())
            addChildHandler( it.next() );
    }

    /** checks the version of the input-file.  throws
     WrongVersionException if the file-version is not supported by
     the reader.
     */
    private void processHead(
        String uri,
        String name,
        RaplaSAXAttributes atts ) throws RaplaSAXParseException
    {
        try
        {
            String version = null;
            getLogger().debug( "Getting version." );
            if (name.equals( "data" ) && uri.equals( RAPLA_NS ))
            {
                version = atts.getValue( "version" );
                if (version == null)
                    throw createSAXParseException( "Could not get Version" );
            }
            String start = atts.getValue( "startDate");
            String end = atts.getValue( "endDate");
            if ( start != null || end != null)
            {
            	Date startDate = start!= null ? parseDate(start, false) : null;
            	Date endDate = end!= null ? parseDate(end, true) : null;
            	if ( startDate != null && startDate.getTime() < DateTools.MILLISECONDS_PER_DAY)
            	{
            		startDate = null;
            	}
            	invalidateInterval = new TimeInterval(startDate, endDate);
            }
            String resourcesRefresh = atts.getValue( "resourcesRefresh");
            if ( resourcesRefresh != null)
            {
            	this.resourcesRefresh = Boolean.parseBoolean( resourcesRefresh);
            }
           
            if (name.equals( "DATA" ))
            {
                version = atts.getValue( "version" );
                if (version == null)
                {
                    version = "0.1";
                }
            }
            if (version == null)
                throw createSAXParseException( "Invalid Format. Could not read data." );

            double versionNr;
            try {
                versionNr = new Double(version).doubleValue();
            } catch (NumberFormatException ex) {
                throw new RaplaException("Invalid version tag (double-value expected)!");
            }
            if (!version.equals( INPUT_FILE_VERSION ))
            {
                // get the version number of the data-schema
                if (versionNr > new Double(RaplaMainReader.INPUT_FILE_VERSION).doubleValue())
                    throw new RaplaException("This version of Rapla cannot read files with a version-number"
                                             + " greater than " + RaplaMainReader.INPUT_FILE_VERSION
                                             + ", try out the latest version.");
                getLogger().warn( "Older version detected. " );
            }
            this.writeableContext.put(VERSION, versionNr);
            
            getLogger().debug( "Found compatible version-number." );
            // We've got the right version. We can proceed.
        }
        catch (Exception ex)
        {
        	throw createSAXParseException(ex.getMessage(),ex);
        }
    }

    @Override
    public void processElement(
        String namespaceURI,
        String localName,
        RaplaSAXAttributes atts ) throws RaplaSAXParseException
    {
        if (level == 1)
        {
            processHead( namespaceURI, localName, atts );
            return;
        }

        if ( !namespaceURI.equals(RAPLA_NS) && !namespaceURI.equals(RELAXNG_NS))
        {
            // Ignore unknown namespace
            return;
        }

        // lookupDeprecated delegation-handler for the localName
        DelegationHandler handler = localnameTable.get( localName );
        // Ignore unknown elements
          if (handler != null)
        {
            delegateElement( handler, namespaceURI, localName, atts );
        }
        
    }

	public TimeInterval getInvalidateInterval() 
	{
		return invalidateInterval;
	}

	public boolean isResourcesRefresh() {
		return resourcesRefresh;
	}

}
