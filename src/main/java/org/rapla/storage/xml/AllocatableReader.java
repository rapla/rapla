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

import org.rapla.components.util.Assert;
import org.rapla.components.util.xml.RaplaSAXAttributes;
import org.rapla.components.util.xml.RaplaSAXParseException;
import org.rapla.entities.Annotatable;
import org.rapla.entities.IllegalAnnotationException;
import org.rapla.entities.domain.ResourceAnnotations;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.domain.internal.PermissionImpl;
import org.rapla.framework.RaplaException;

public class AllocatableReader extends RaplaXMLReader
{
    private DynAttReader dynAttHandler;
    private AllocatableImpl allocatable;
	private String annotationKey;
	private Annotatable currentAnnotatable;
	private PermissionReader permissionHandler;
	
    public AllocatableReader( RaplaXMLContext context ) throws RaplaException
    {
        super( context );
        dynAttHandler = new DynAttReader( context );
        permissionHandler = new PermissionReader( context );
        addChildHandler( dynAttHandler );
        addChildHandler( permissionHandler );
    }

    @Override
    public void processElement(
        String namespaceURI,
        String localName,
        RaplaSAXAttributes atts ) throws RaplaSAXParseException
    {
        if (namespaceURI.equals( DYNATT_NS ) || namespaceURI.equals( EXTENSION_NS ))
        {
            if (  localName.equals("rapla:crypto"))
            {
                return;
            }
            dynAttHandler.setClassifiable( allocatable );
            delegateElement(
                dynAttHandler,
                namespaceURI,
                localName,
                atts );
            return;
        }

        if (!namespaceURI.equals( RAPLA_NS ))
            return;

        if (localName.equals( "permission" ))
        {
            permissionHandler.setContainer( allocatable);
            delegateElement(
                    permissionHandler,
                    namespaceURI,
                    localName,
                    atts );
            return;
            
        }
        else if (localName.equals( "annotation" ) )
        {
            annotationKey = atts.getValue( "key" );
            Assert.notNull( annotationKey, "key attribute cannot be null" );
            startContent();
        }
        else
        {
        	TimestampDates ts = readTimestamps( atts);
            allocatable = new AllocatableImpl(ts.createTime, ts.changeTime);
            allocatable.setResolver( store );
            currentAnnotatable = allocatable;
            setId( allocatable, atts );
            // support old holdback conflicts behaviour
            {
            	String holdBackString = getString( atts, "holdbackconflicts", "false" );
                if ( Boolean.valueOf( holdBackString ) )
            	{
            		try {
						allocatable.setAnnotation(ResourceAnnotations.KEY_CONFLICT_CREATION, ResourceAnnotations.VALUE_CONFLICT_CREATION_IGNORE);
					} catch (IllegalAnnotationException e) {
						throw createSAXParseException(e.getMessage(),e);
					}
            	}
            }
            setLastChangedBy(allocatable, atts);
            setOwner(allocatable, atts);
        }
        
    }

    @Override
    public void processEnd( String namespaceURI, String localName ) throws RaplaSAXParseException
    {
        if (!namespaceURI.equals( RAPLA_NS ))
            return;

        if (localName.equals( "resource" ) || localName.equals( "person" ) )
        {
            if (allocatable.getPermissionList().size() == 0)
                allocatable.addPermission( new PermissionImpl() );
            add( allocatable );
        }
        else if (localName.equals( "extension" ) )
        {
            if ( allocatable.getClassification() != null)
            {
                add( allocatable );
            }
        }
        else if (localName.equals( "annotation" ) )
        {
            try
            {
                String annotationValue = readContent().trim();
				currentAnnotatable.setAnnotation( annotationKey, annotationValue );
            }
            catch (IllegalAnnotationException ex)
            {
            }
        }
    }
}
