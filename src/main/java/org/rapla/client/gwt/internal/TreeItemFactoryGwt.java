package org.rapla.client.gwt.internal;

import org.rapla.client.internal.TreeFactoryImpl;
import org.rapla.client.RaplaTreeNode;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;

@DefaultImplementation(of= TreeFactoryImpl.TreeItemFactory.class,context = InjectionContext.gwt)
public class TreeItemFactoryGwt implements TreeFactoryImpl.TreeItemFactory
{
    @Inject
    public TreeItemFactoryGwt()
    {

    }
    @Override
    public RaplaTreeNode createNode(Object userObject)
    {
        throw new UnsupportedOperationException();
    }
}
