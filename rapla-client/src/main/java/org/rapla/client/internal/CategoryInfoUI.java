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
import org.rapla.entities.User;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;

public class CategoryInfoUI extends HTMLInfo<Category> {
    public CategoryInfoUI(RaplaFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger){
        super(i18n, raplaLocale, facade, logger);
    }

    @Override
    public String createHTMLAndFillLinks(Category category,LinkController controller, User user) throws RaplaException{
        return category.getName( getRaplaLocale().getLocale());
    }
}