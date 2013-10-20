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

import java.io.IOException;

import org.rapla.entities.Category;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.storage.RefEntity;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;

public class UserWriter extends RaplaXMLWriter {
    
    public UserWriter(RaplaContext sm) throws RaplaException {
        super(sm);
    }

    public void printUser(User user, boolean includePassword, boolean includePreference) throws IOException,RaplaException {
       
        openTag("rapla:user");
        printId(user);
        printVersion( user);
        att("username",user.getUsername());
        if ( includePassword)
        {
            String password = cache.getPassword(((RefEntity<?>)user).getId());
            if ( password != null )
            {
                att("password",password);
                //System.out.println("Writing password to file " + password);
            }
            
        }
            
        att("name",user.getName());
        att("email",user.getEmail());
        att("isAdmin",String.valueOf(user.isAdmin()));
        closeTag();
        
        Category[] groups = user.getGroups();
        for ( int i = 0; i < groups.length; i++ ) {
            Category group = groups[i];
            String groupPath = getGroupPath( group );
            String id = getId( group );
            try
            {
	            openTag("rapla:group");
	            if ( isIdOnly() ) {
					att( "idref", id );
	            } else {
					att( "key", groupPath );
	            }
	            closeElementTag();
            }
            catch (Exception ex)
            {
            	getLogger().error(ex.getMessage(), ex);
            }
        }

        if ( includePreference)
        {
            Preferences preferences = cache.getPreferences(user);
            if ( preferences != null) {
                PreferenceWriter preferenceWriter = (PreferenceWriter) getWriterFor(Preferences.TYPE);
                preferenceWriter.setIndentLevel( getIndentLevel() );
                preferenceWriter.printPreferences(preferences);
            }
        }

        closeElement("rapla:user");
    }
    
    public void writeObject(RaplaObject object) throws IOException, RaplaException {
        printUser( (User) object,false,true);
    }


    private String getGroupPath( Category category) throws EntityNotFoundException {
        Category rootCategory = cache.getSuperCategory().getCategory(Permission.GROUP_CATEGORY_KEY);
        return ((CategoryImpl) rootCategory ).getPathForCategory(category);
    }
    
    public void printUsers()  throws IOException,RaplaException {
        openElement("rapla:users");
        println("<!-- Users of the system -->");
        for (User user: cache.getCollection( User.class)) {
            printUser( user, true, true);
        }
        closeElement("rapla:users");
    }



}


