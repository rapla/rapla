package org.rapla.client.swing.internal.view;

import org.rapla.client.RaplaTreeNode;
import org.rapla.client.dialog.InfoFactory;
import org.rapla.client.swing.toolkit.TreeToolTipRenderer;
import org.rapla.facade.Conflict;

import javax.swing.*;

public class RaplaTreeToolTipRenderer implements TreeToolTipRenderer
{
    private final InfoFactory infoFactory;

    public RaplaTreeToolTipRenderer(InfoFactory infoFactory) {
        this.infoFactory = infoFactory;
    }

    public String getToolTipText(JTree tree, int row)
    {
        Object node = tree.getPathForRow(row).getLastPathComponent();
        Object value = null;
        if (node instanceof RaplaTreeNode)
            value = ((RaplaTreeNode) node).getUserObject();
        if (value instanceof Conflict)
        {
            return null;
        }
        return infoFactory.getToolTip(value);
    }
}
