package org.rapla.client.swing.internal.view;

import java.util.function.Predicate;

public interface RaplaTreeNode
{
    Object getUserObject();
    int getChildCount();
    RaplaTreeNode getChild(int index);
    void add(RaplaTreeNode childNode);
    void remove(RaplaTreeNode childNode);

    static int getRecursiveCount(RaplaTreeNode treeNode, Predicate predicate)
    {
        int count = 0;
        Object userObject = treeNode.getUserObject();
        if (userObject != null && predicate.test(userObject))
        {
            count++;
        }
        int children = treeNode.getChildCount();
        if (children == 0)
        {
            return count;
        }
        for (int i = 0; i < children; i++)
        {
            RaplaTreeNode child = treeNode.getChild(i);
            count+= getRecursiveCount(child,predicate);
        }
        return count;
    }


}
