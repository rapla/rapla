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

import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.TreeFactory;
import org.rapla.client.swing.internal.view.RaplaSwingTreeModel;
import org.rapla.client.RaplaTreeNode;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;

public class AllocatableSelectField extends AbstractSelectField<Allocatable>
{
	DynamicType dynamicTypeConstraint;
    public AllocatableSelectField(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, TreeFactory treeFactory,  DynamicType dynamicTypeConstraint, DialogUiFactoryInterface dialogUiFactory){
       super( facade, i18n, raplaLocale, logger, treeFactory,  dialogUiFactory);
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
		return selectedAllocatable.getName(i18n.getLocale());
	}

    @Override
	public TreeModel createModel() throws RaplaException {
		Allocatable[] allocatables = getAllocatables();
		TreeModel treeModel;
		RaplaTreeNode rootNode = getTreeFactory().createClassifiableModel(allocatables, true);
		if (dynamicTypeConstraint !=null)
		{
			if ( rootNode.getChildCount() > 0)
			{
			    RaplaTreeNode child = rootNode.getChild(0);
			    rootNode.remove( child);
			    treeModel = new RaplaSwingTreeModel( child );
			}
			else
			{
			    treeModel = new DefaultTreeModel( new DefaultMutableTreeNode());
			}
		}
		else
		{
			treeModel = new RaplaSwingTreeModel( rootNode);
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
            allocatables = raplaFacade.getAllocatablesWithFilter(filters);
        }
        else
        {
            allocatables = raplaFacade.getAllocatables();
        }
		return allocatables;
	}

}


