
/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copyReservations of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.client.internal;

import org.rapla.RaplaResources;
import org.rapla.entities.DependencyException;
import org.rapla.entities.User;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.storage.StorageOperator;

import java.util.Iterator;

class DependencyInfoUI extends HTMLInfo<DependencyException> {
    public DependencyInfoUI(RaplaResources i18n, RaplaLocale raplaLocale, RaplaFacade facade, Logger logger){
        super(i18n, raplaLocale, facade, logger);
    }

    @Override
    public String createHTMLAndFillLinks(DependencyException ex,LinkController controller, User user) throws RaplaException{
        StringBuffer buf = new StringBuffer();
        buf.append(getString("error.dependencies")+":");
        buf.append("<br>");
        Iterator<String> it = ex.getDependencies().iterator();
        int i = 0;
        while (it.hasNext()) {
            Object obj = it.next();
            buf.append((++i));
            buf.append(") ");
            buf.append( obj );
            buf.append("<br>");
            if (i >= StorageOperator.MAX_DEPENDENCY && it.hasNext()) { //BJO
                buf.append("...  more"); //BJO
                break;
            }
        }
        return buf.toString();
    }
    
    @Override
    public String getTitle(DependencyException ex) {
        return getString("info") + ": " + getString("error.dependencies");
    }
}














