package org.rapla.client.gwt.internal;

import org.rapla.client.RaplaTreeNode;
import org.rapla.client.internal.TreeItemFactory;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;

@DefaultImplementation(of= TreeItemFactory.class,context = InjectionContext.gwt)
public class TreeItemFactoryGwt implements TreeItemFactory
{
    @Inject
    public TreeItemFactoryGwt()
    {

    }
    @Override
    public RaplaTreeNode createNode(Object userObject)
    {
        // return new VueTreeNode(userObject);
        throw new UnsupportedOperationException();
    }
}
