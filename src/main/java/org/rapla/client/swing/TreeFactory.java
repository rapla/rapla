package org.rapla.client.swing;

import org.rapla.client.swing.toolkit.TreeToolTipRenderer;
import org.rapla.entities.Category;
import org.rapla.entities.Named;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.Conflict;
import org.rapla.framework.RaplaException;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeModel;
import java.util.Collection;

public interface TreeFactory {
	
	TreeModel createClassifiableModel(Allocatable[] classifiables, boolean useCategorizations);

	TreeModel createConflictModel(Collection<Conflict> conflicts ) throws RaplaException;
	
    DefaultMutableTreeNode newNamedNode(Named element);

	TreeModel createModel(Category category);
	
	TreeModel createModel(Collection<Category> categories, boolean includeChildren);
	
	TreeModel createModelFlat(Named[] element);

	TreeToolTipRenderer createTreeToolTipRenderer();
	TreeCellRenderer createConflictRenderer();
	TreeCellRenderer createRenderer();
}