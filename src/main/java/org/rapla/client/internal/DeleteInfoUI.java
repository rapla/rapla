
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
import org.rapla.entities.User;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;

public class DeleteInfoUI extends HTMLInfo<Object[]> {
    public DeleteInfoUI(RaplaResources i18n, RaplaLocale raplaLocale, RaplaFacade facade, Logger logger) {
        super(i18n, raplaLocale, facade, logger);
    }

    public String createHTMLAndFillLinks(Object[] deletables,LinkController controller, User user) {
        StringBuffer buf = new StringBuffer();
        buf.append(getString("delete.question"));
        buf.append("<br>");
        for (int i = 0; i<deletables.length; i++) {
            buf.append((i + 1));
            buf.append(") ");
            final Object deletable = deletables[i];
            controller.createLink( deletable, getName( deletable ), buf);
            buf.append("<br>");
        }
        return buf.toString();
    }
    
    @Override
    public String getTitle(Object[] deletables){
        return getString("delete.title");
    }

   
}














