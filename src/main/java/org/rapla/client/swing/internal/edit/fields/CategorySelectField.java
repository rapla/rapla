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
package org.rapla.client.swing.internal.edit.fields;

import javax.swing.tree.TreeModel;

import org.rapla.client.swing.TreeFactory;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.toolkit.DialogUI.DialogUiFactory;
import org.rapla.entities.Category;
import org.rapla.framework.RaplaContext;


public class CategorySelectField extends AbstractSelectField<Category>
{
    Category rootCategory;

    public CategorySelectField(RaplaContext context, TreeFactory treeFactory, RaplaImages raplaImages, DialogUiFactory dialogUiFactory, Category rootCategory){
       this( context, treeFactory, raplaImages, dialogUiFactory, rootCategory, null);
    }
    
    public CategorySelectField(RaplaContext context, TreeFactory treeFactory, RaplaImages raplaImages, DialogUiFactory dialogUiFactory, Category rootCategory, Category defaultCategory)
    {
        super( context, treeFactory,raplaImages, dialogUiFactory, defaultCategory);
        this.rootCategory = rootCategory;
    }
    
    @Override
	protected String getNodeName(Category selectedCategory)
	{
		return selectedCategory.getPath(rootCategory,getI18n().getLocale());
	}

    @Override
	public TreeModel createModel() {
		return getTreeFactory().createModel(rootCategory);
    }

    public Category getRootCategory() 
    {
        return rootCategory;
    }

    public void setRootCategory(Category rootCategory) 
    {
        this.rootCategory = rootCategory;
    }

}


