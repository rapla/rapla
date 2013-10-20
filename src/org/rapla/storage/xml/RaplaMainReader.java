/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Christopher Kohlhaas                                  |
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
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.Conflict;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

public class RaplaMainReader extends RaplaXMLReader
{
    private Map<String,RaplaXMLReader> localnameTable = new HashMap<String,RaplaXMLReader>();
    public final static String INPUT_FILE_VERSION = RaplaXMLWriter.OUTPUT_FILE_VERSION;
    private TimeInterval invalidateInterval = null;
    private boolean resourcesRefresh = false;
    
  
	public RaplaMainReader( RaplaContext context ) throws RaplaException
    {
        super( context );
      
        // Setup the delegation classes
        localnameTable.put( "grammar", readerMap.get( DynamicType.TYPE ) );
        localnameTable.put( "element", readerMap.get( DynamicType.TYPE ) );

        localnameTable.put( "user", readerMap.get( User.TYPE ) );
        localnameTable.put( "category", readerMap.get( Category.TYPE ) );
        localnameTable.put( "preferences", readerMap.get( Preferences.TYPE ) );
        localnameTable.put( "resource", readerMap.get( Allocatable.TYPE ) );
        localnameTable.put( "person", readerMap.get( Allocatable.TYPE ) );
        localnameTable.put( "period", readerMap.get( Period.TYPE ) );
        localnameTable.put( "reservation", readerMap.get( Reservation.TYPE ) );
        localnameTable.put( "conflict", readerMap.get( Conflict.TYPE ) );
        localnameTable.put( "remove", readerMap.get( "remove") );
        localnameTable.put( "store", readerMap.get( "store") );
        localnameTable.put( "reference", readerMap.get( "reference") );
        addChildHandler( readerMap.values() );
    }

    public void setDocumentLocator( Locator locator )
    {
        super.setDocumentLocator( locator );
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
        @SuppressWarnings("unused") String qName,
        Attributes atts ) throws SAXException
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
            String repositoryVersion = atts.getValue("repositoryVersion");
            if ( repositoryVersion != null)
            {
                resolver.setRepositoryVersion( Long.parseLong( repositoryVersion));
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

            if (!version.equals( INPUT_FILE_VERSION ))
            {
                getLogger().warn( "Warning: Different version detected" );
                throw new WrongVersionException( version );
            }
            getLogger().debug( "Found compatible version-number." );
            // We've got the right version. We can proceed.
        }
        catch (Exception ex)
        {
            throw new SAXException( ex );
        }
    }

    public void processElement(
        String namespaceURI,
        String localName,
        String qName,
        Attributes atts ) throws SAXException
    {
        if (level == 1)
        {
            processHead( namespaceURI, localName, qName, atts );
            return;
        }

        if ( !namespaceURI.equals(RAPLA_NS) && !namespaceURI.equals(RELAXNG_NS))
        {
            // Ignore unknown namespace
            return;
        }

        // lookup delegation-handler for the localName
        DelegationHandler handler = localnameTable.get( localName );
        // Ignore unknown elements
          if (handler != null)
        {
            delegateElement( handler, namespaceURI, localName, qName, atts );
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
