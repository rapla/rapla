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

import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.swing.TreeFactory;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.entities.Category;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;


public class CategorySelectField extends AbstractSelectField<Category>
{
    Category rootCategory;

    public CategorySelectField(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, TreeFactory treeFactory, RaplaImages raplaImages, DialogUiFactoryInterface dialogUiFactory, Category rootCategory){
       this( facade, i18n, raplaLocale, logger, treeFactory, raplaImages, dialogUiFactory, rootCategory, null);
    }
    
    public CategorySelectField(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, TreeFactory treeFactory, RaplaImages raplaImages, DialogUiFactoryInterface dialogUiFactory, Category rootCategory, Category defaultCategory)
    {
        super( facade, i18n, raplaLocale, logger, treeFactory,raplaImages, dialogUiFactory, defaultCategory);
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


