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
package org.rapla.client.internal;

import org.rapla.RaplaResources;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;

import java.util.ArrayList;
import java.util.Collection;

public class UserInfoUI extends HTMLInfo<User> {
	ClassificationInfoUI<Allocatable> classificationInfo;
    ClientFacade clientFacade;
    public UserInfoUI(ClientFacade clientFacade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger) {
        super(i18n, raplaLocale, clientFacade.getRaplaFacade(), logger);
        classificationInfo = new ClassificationInfoUI<>(i18n, raplaLocale, clientFacade.getRaplaFacade(), logger);
        this.clientFacade = clientFacade;
    }

    @Override
    public String createHTMLAndFillLinks(User user,LinkController controller, User forUser) {
        StringBuffer buf = new StringBuffer();
        if (user.isAdmin()) {
            highlight(getString("admin"),buf);
        }
        Collection<Row> att = new ArrayList<>();
        att.add(new Row(getString("username"), strong( encode( user.getUsername() ) ) ) );
        
        final Allocatable person = user.getPerson();
        if ( person == null)
        {
            att.add(new Row(getString("name"), encode(user.getName())));
            att.add(new Row(getString("email"), encode(user.getEmail())));
        }
        else
        {
            // no links for user resource to its person so we pass null as link controller
            Collection<Row> classificationAttributes = classificationInfo.getClassificationAttributes(person, false, null, user);
			att.addAll(classificationAttributes);
        }
        att.add(new Row("Rapla Id", user.getId()));
        createTable(att,buf,false);
        
        Category userGroupsCategory;
		try {
			userGroupsCategory = getQuery().getUserGroupsCategory();
		} catch (RaplaException e) {
			// Should not happen, but null doesnt harm anyway
			userGroupsCategory = null;
		}
        Collection<Category> groups = user.getGroupList();
        if ( groups.size() > 0 ) {
            buf.append(getString("groups") + ":");
            buf.append("<ul>");
            for ( Category group:groups) {
                buf.append("<li>");
                String groupName = group.getPath( userGroupsCategory , getI18n().getLocale());
                encode ( groupName , buf);
                buf.append("</li>\n");
            }
            buf.append("</ul>");
        }
        return buf.toString();
    }
    
    @Override
    public String getTooltip(User user, User forUser) {
        return createHTMLAndFillLinks(user, null, forUser );
    }

}
