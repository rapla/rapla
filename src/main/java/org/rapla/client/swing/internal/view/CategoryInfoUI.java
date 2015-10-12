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
package org.rapla.client.swing.internal.view;

import org.rapla.entities.Category;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;

class CategoryInfoUI extends HTMLInfo<Category> {
    public CategoryInfoUI(RaplaContext sm){
        super(sm);
    }

    protected String createHTMLAndFillLinks(Category category,LinkController controller) throws RaplaException{
        return category.getName( getRaplaLocale().getLocale());
    }
}