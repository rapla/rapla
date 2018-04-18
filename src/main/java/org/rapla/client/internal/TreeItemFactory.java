package org.rapla.client.internal;

import org.rapla.client.RaplaTreeNode;

public interface TreeItemFactory
{
    RaplaTreeNode createNode(Object userObject);
}
