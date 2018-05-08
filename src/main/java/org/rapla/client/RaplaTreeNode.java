package org.rapla.client;

public interface RaplaTreeNode
{
    Object getUserObject();
    int getChildCount();
    RaplaTreeNode getChild(int index);
    void add(RaplaTreeNode childNode);
    void remove(RaplaTreeNode childNode);
}
