package org.rapla.client.swing;

import org.rapla.client.swing.internal.view.RaplaTreeNode;
import org.rapla.entities.Category;
import org.rapla.entities.Named;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.Conflict;
import org.rapla.framework.RaplaException;

import javax.swing.tree.TreeModel;
import java.util.Collection;

public interface TreeFactory {
	
	RaplaTreeNode createClassifiableModel(Allocatable[] classifiables, boolean useCategorizations);

	RaplaTreeNode createConflictModel(Collection<Conflict> conflicts ) throws RaplaException;
	
    RaplaTreeNode newNamedNode(Named element);

	RaplaTreeNode createModel(Collection<Category> categories, boolean includeChildren);

	RaplaTreeNode newStringNode(String s);

	RaplaTreeNode newRootNode();
}