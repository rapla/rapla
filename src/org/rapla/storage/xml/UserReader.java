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

import java.util.Date;

import org.rapla.components.util.xml.RaplaSAXAttributes;
import org.rapla.components.util.xml.RaplaSAXParseException;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.internal.UserImpl;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;

public class UserReader extends RaplaXMLReader
{
    UserImpl user;
    PreferenceReader preferenceHandler;

    public UserReader( RaplaContext context ) throws RaplaException
    {
        super( context );
        preferenceHandler = new PreferenceReader( context );
        addChildHandler( preferenceHandler );
    }

    @Override
    public void processElement(
        String namespaceURI,
        String localName,
        RaplaSAXAttributes atts ) throws RaplaSAXParseException
    {
        if (!namespaceURI.equals( RAPLA_NS ))
            return;

        if (localName.equals( "user" ))
        {
            TimestampDates ts = readTimestamps( atts);
            user = new UserImpl(ts.createTime, ts.changeTime);
            String id = setId( user, atts );
//            String idString = getString(atts, "person",null);
//            if ( idString != null)
//            {
//                String personId = getId(Allocatable.TYPE,idString);
//                user.putId("person",personId);
//            }
            user.setUsername( getString( atts, "username", "" ) );
            user.setName( getString( atts, "name", "" ) );
            user.setEmail( getString( atts, "email", "" ) );
            user.setAdmin( getString( atts, "isAdmin", "false" ).equals( "true" ) );
            String password = getString( atts, "password", null );
            preferenceHandler.setUser( user );
            if ( password != null)
            {
                putPassword( id, password );
            }
        }

        if (localName.equals( "group" ))
        {
            
            String groupId = atts.getValue( "idref" );
            if (groupId !=null)
            {
            	String newGroupId = getId(Category.TYPE, groupId);
                user.addId("groups",newGroupId);
            }
            else
            {
                String groupKey = getString( atts, "key" );
                Category group = getGroup( groupKey);
                if (group != null)
                {
                    user.addGroup( group );
                }
            }
        }

        if (localName.equals( "preferences" ))
        {
            delegateElement(
                preferenceHandler,
                namespaceURI,
                localName,
                atts );
        }
    }
    
    @SuppressWarnings("deprecation")
    @Override
    public void processEnd( String namespaceURI, String localName ) throws RaplaSAXParseException
    {
        if (!namespaceURI.equals( RAPLA_NS ))
            return;

        if (localName.equals( "user" ))
        {
            preferenceHandler.setUser( null );
            if ( isBefore1_2())
            {
                addNewGroup(user, Permission.GROUP_CAN_READ_EVENTS_FROM_OTHERS);
                addNewGroup(user, Permission.GROUP_CAN_CREATE_EVENTS);
            }
            add( user );
        }
    }
    
    private void addNewGroup(User user, String groupKey) throws RaplaSAXParseException {
        Category userGroups = getSuperCategory().getCategory(Permission.GROUP_CATEGORY_KEY);
        Category group = userGroups.getCategory(groupKey);
        if ( group != null)
        {   
            // add the groups to the user if the groups were not there in a previous version
            Date createTime = group.getCreateTime();
            RaplaXMLReader dynamicTypeReader = getChildHandlerForType(DynamicType.TYPE);
            Date categoryCreateTime = dynamicTypeReader.getReadTimestamp();
            if ( categoryCreateTime.equals(createTime ))
            {
                user.addGroup( group);
            }
        }
    }

}
