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
package org.rapla.gui.internal.edit.fields;

import java.util.Vector;

import org.rapla.entities.Category;
import org.rapla.framework.RaplaContext;

public class CategoryListField extends ListField<Category>  {
    Category rootCategory;

    public CategoryListField(RaplaContext context,Category rootCategory) {
        super(context, true);
        this.rootCategory = rootCategory;

        Category[] obj = rootCategory.getCategories();
        Vector<Category> list = new Vector<Category>();
        for (int i=0;i<obj.length;i++)
            list.add(obj[i]);
        setVector(list);
    }
}

