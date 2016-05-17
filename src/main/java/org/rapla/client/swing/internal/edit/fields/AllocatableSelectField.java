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

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;

import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.swing.TreeFactory;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;

public class AllocatableSelectField extends AbstractSelectField<Allocatable>
{
	DynamicType dynamicTypeConstraint;
    public AllocatableSelectField(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, TreeFactory treeFactory, RaplaImages raplaImages, DynamicType dynamicTypeConstraint, DialogUiFactoryInterface dialogUiFactory){
       super( facade, i18n, raplaLocale, logger, treeFactory, raplaImages, dialogUiFactory);
       this.dynamicTypeConstraint = dynamicTypeConstraint;
    }
    
    @Override
	protected Allocatable getValue(Object valueObject) {
    	if ( valueObject instanceof Allocatable)
    	{
    		return (Allocatable) valueObject;
    	}
    	else
    	{
    		return null;
    	}
	}

	
    @Override
	protected String getNodeName(Allocatable selectedAllocatable)
	{
		return selectedAllocatable.getName(getI18n().getLocale());
	}

    @Override
	public TreeModel createModel() throws RaplaException {
		Allocatable[] allocatables = getAllocatables();
		TreeModel treeModel = getTreeFactory().createClassifiableModel(allocatables, true);
		if (dynamicTypeConstraint !=null)
		{
			TreeNode treeNode = (TreeNode)treeModel.getRoot();
			if ( treeNode.getChildCount() > 0)
			{
			    TreeNode child = treeNode.getChildAt(0);
			    ((DefaultMutableTreeNode)child).removeFromParent();
			    treeModel = new DefaultTreeModel( child );
			}
			else
			{
			    treeModel = new DefaultTreeModel( new DefaultMutableTreeNode());
			}
		}
		return treeModel;
    }

	protected Allocatable[] getAllocatables() throws RaplaException
	{
		Allocatable[] allocatables;
		if (dynamicTypeConstraint !=null)
        {
			ClassificationFilter filter = dynamicTypeConstraint.newClassificationFilter();
            ClassificationFilter[] filters = new ClassificationFilter[] {filter};
            allocatables = getQuery().getAllocatables(filters);
        }
        else
        {
            allocatables = getQuery().getAllocatables();
        }
		return allocatables;
	}

}


