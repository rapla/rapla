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

import java.io.IOException;

import org.rapla.entities.Category;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.framework.RaplaException;

public class UserWriter extends RaplaXMLWriter {
    
    public UserWriter(RaplaXMLContext sm) throws RaplaException {
        super(sm);
    }

    public void printUser(User user, String password, Preferences preferences) throws IOException,RaplaException {
       
        openTag("rapla:user");
        printId(user);
        printTimestamp( user);
        att("username",user.getUsername());
        if ( password != null )
        {
            att("password",password);
        }
            
        att("name",user.getName());
        att("email",user.getEmail());
//        Allocatable person = user.getPerson();
//        if ( person != null)
//        {
//            att("person", person.getId());
//        }
        att("isAdmin",String.valueOf(user.isAdmin()));
        closeTag();
        
        for (Category group:user.getGroupList()) {
            String groupPath = getGroupPath( group );
            try
            {
	            openTag("rapla:group");
	            att( "key", groupPath );
	            closeElementTag();
            }
            catch (Exception ex)
            {
            	getLogger().error(ex.getMessage(), ex);
            }
        }

        if ( preferences != null) {
            PreferenceWriter preferenceWriter = (PreferenceWriter) getWriterFor(Preferences.class);
            preferenceWriter.setIndentLevel( getIndentLevel() );
            preferenceWriter.printPreferences(preferences);
        }
        closeElement("rapla:user");
    }
    
    public void writeObject(RaplaObject object) throws IOException, RaplaException {
        printUser( (User) object,null,null);
    }


    private String getGroupPath( Category category) throws EntityNotFoundException {
        Category rootCategory = getSuperCategory().getCategory(Permission.GROUP_CATEGORY_KEY);
        return ((CategoryImpl) rootCategory ).getPathForCategory(category);
    }
    


}


