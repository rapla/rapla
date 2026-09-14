package org.rapla.client.swing.internal.view;

import org.rapla.client.RaplaTreeNode;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

public class RaplaSwingTreeModel extends DefaultTreeModel {
    public RaplaSwingTreeModel(RaplaTreeNode treeNode)
    {
        super((TreeNode) treeNode);
    }
}
