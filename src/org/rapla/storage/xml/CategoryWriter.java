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
import org.rapla.entities.RaplaObject;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;

public class CategoryWriter extends RaplaXMLWriter {
    public CategoryWriter(RaplaContext sm) throws RaplaException {
        super(sm);
    }
    
    public void printRaplaType(RaplaObject type) throws RaplaException, IOException {
        printCategory( (Category) type);
    }
    
    public void printCategory(Category category) throws IOException,RaplaException {
        printCategory( category, true);
    }
    
    public void printCategory(Category category,boolean printSubcategories) throws IOException,RaplaException {
        openTag("rapla:category");
        printTimestamp( category);
        printId(category);
//        if (isPrintId())
//        {
//            Category parent = category.getParent();
//            if ( parent != null)
//            {
//                att("parentid", getId( parent));
//            }
//        }
        att("key",category.getKey());
        closeTag();
        printTranslation(category.getName());
        printAnnotations( category, false);
        if ( printSubcategories )
        {
        	Category[] categories = category.getCategories();
        	for (int i=0;i<categories.length;i++)
        		printCategory(categories[i]);
        }
        closeElement("rapla:category");
    }
    
    public void writeObject(RaplaObject persistant) throws RaplaException, IOException {
        printCategory( (Category) persistant, false);
    }



}


