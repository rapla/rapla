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

import org.rapla.components.util.xml.RaplaSAXAttributes;
import org.rapla.components.util.xml.RaplaSAXParseException;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.PermissionContainer;
import org.rapla.entities.domain.internal.PermissionImpl;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.entities.storage.internal.ReferenceHandler;
import org.rapla.framework.RaplaException;

public class PermissionReader extends RaplaXMLReader
{
	
    PermissionContainer permissionContainer;
    
    public PermissionReader( RaplaXMLContext context ) throws RaplaException
    {
        super( context );
    }

    @Override
    public void processElement(
        String namespaceURI,
        String localName,
        RaplaSAXAttributes atts ) throws RaplaSAXParseException
    {
        if (!namespaceURI.equals( RAPLA_NS ))
            return;

        if (localName.equals( "permission" ))
        {
            PermissionImpl permission = new PermissionImpl();
            permission.setResolver( store );
            // process user
            String userString = atts.getValue( "user" );
            ReferenceHandler referenceHandler = permission.getReferenceHandler();
            if (userString != null)
            {
                referenceHandler.putId("user", getId(User.class,userString));
            }

            // process group
            String groupId = atts.getValue( "groupidref" );
            if (groupId != null)
            {
            	referenceHandler.putId("group", getId(Category.class,groupId));
            }
            else
            {
                String groupName = atts.getValue( "group" );
                if (groupName != null)
                {
                    ReferenceInfo<Category> group= getGroupWithKeyRef(groupName);
                    permission.setGroupId(group);
                }
            }

            String startDate = getString( atts, "start-date", null );
            if (startDate != null)
            {
                permission.setStart( parseDate( startDate, false ) );
            }

            String endDate = getString( atts, "end-date", null );
            if (endDate != null)
            {
                permission.setEnd( parseDate( endDate, false ) );
            }

            String minAdvance = getString( atts, "min-advance", null );
            if (minAdvance != null)
            {
                permission.setMinAdvance( parseLong( minAdvance ).intValue() );
            }

            String maxAdvance = getString( atts, "max-advance", null );
            if (maxAdvance != null)
            {
                permission.setMaxAdvance( parseLong( maxAdvance ).intValue() );
            }

            String accessLevel = getString(
                atts,
                "access",
                Permission.ALLOCATE_CONFLICTS.name() );
            Permission.AccessLevel matchingLevel = Permission.AccessLevel.find( accessLevel);
            if (matchingLevel  == null)
            {
                throw createSAXParseException( "Unknown access level '" + accessLevel + "'" );
            }
            permission.setAccessLevel( matchingLevel );
            permissionContainer.addPermission( permission );
        }
    }

    @Override
    public void processEnd( String namespaceURI, String localName ) throws RaplaSAXParseException
    {
        if (!namespaceURI.equals( RAPLA_NS ))
            return;

    }

    public void setContainer(PermissionContainer permissionContainer) {
        this.permissionContainer = permissionContainer;
    }
}
