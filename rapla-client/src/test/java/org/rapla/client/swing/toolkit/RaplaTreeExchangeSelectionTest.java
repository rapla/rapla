package org.rapla.client.swing.toolkit;

import org.junit.jupiter.api.Test;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link RaplaTree#exchangeTreeModel(TreeModel)} preserves selection
 * across a model swap when the new tree contains user-objects that
 * {@code .equals()} the previously-selected ones. The same contract the
 * ConflictSelectionViewSwing.updateTree path relies on after a reservation
 * move replaces the conflict list.
 */
class RaplaTreeExchangeSelectionTest
{
    @Test
    void selectionPreservedWhenSameUserObjectExistsInNewModel()
    {
        RaplaTree tree = new RaplaTree();

        // Initial model: root → "A" → "B"   (root is invisible by default in RaplaTree)
        DefaultMutableTreeNode oldRoot = new DefaultMutableTreeNode("root");
        DefaultMutableTreeNode oldA = new DefaultMutableTreeNode("A");
        DefaultMutableTreeNode oldB = new DefaultMutableTreeNode("B");
        oldRoot.add(oldA);
        oldRoot.add(oldB);
        tree.exchangeTreeModel(new DefaultTreeModel(oldRoot));

        // Select "A".
        tree.getTree().setSelectionPath(new TreePath(new Object[] { oldRoot, oldA }));
        assertEquals(List.of("A"), tree.getSelectedElements(false));

        // Replace the model with fresh node instances carrying the SAME user-object Strings.
        DefaultMutableTreeNode newRoot = new DefaultMutableTreeNode("root");
        DefaultMutableTreeNode newA = new DefaultMutableTreeNode("A");
        DefaultMutableTreeNode newB = new DefaultMutableTreeNode("B");
        newRoot.add(newA);
        newRoot.add(newB);
        tree.exchangeTreeModel(new DefaultTreeModel(newRoot));

        assertTrue(tree.getSelectedElements(false).contains("A"),
                "exchangeTreeModel must re-apply selection on the new model by user-object equals");
    }

    @Test
    void selectionDroppedWhenUserObjectMissingFromNewModel()
    {
        RaplaTree tree = new RaplaTree();
        DefaultMutableTreeNode oldRoot = new DefaultMutableTreeNode("root");
        DefaultMutableTreeNode oldA = new DefaultMutableTreeNode("A");
        oldRoot.add(oldA);
        tree.exchangeTreeModel(new DefaultTreeModel(oldRoot));
        tree.getTree().setSelectionPath(new TreePath(new Object[] { oldRoot, oldA }));

        DefaultMutableTreeNode newRoot = new DefaultMutableTreeNode("root");
        newRoot.add(new DefaultMutableTreeNode("B"));
        tree.exchangeTreeModel(new DefaultTreeModel(newRoot));

        assertEquals(List.of(), tree.getSelectedElements(false),
                "if the previously-selected user-object isn't in the new model, selection clears");
    }
}
