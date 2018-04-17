package org.rapla.client.swing.internal.view;

import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

public class RaplaSwingTreeModel extends DefaultTreeModel {
    public RaplaSwingTreeModel(RaplaTreeNode treeNode)
    {
        super((TreeNode) treeNode);
    }
}
