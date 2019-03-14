package org.rapla.client.swing.internal.view;

import org.rapla.RaplaResources;
import org.rapla.client.internal.TreeFactoryImpl;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaLocale;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

public class SimpleTreeCellRenderer extends DefaultTreeCellRenderer
{
    private final RaplaResources i18n;
    private final RaplaLocale raplaLocale;
    Font normalFont = UIManager.getFont("Tree.font");

    Icon folderClosedIcon;
    Icon folderOpenIcon;
    Icon defaultIcon;
    Icon personIcon;
    private static final long serialVersionUID = 1L;
    Border conflictBorder = BorderFactory.createEmptyBorder(2, 0, 2, 0);

    @Inject
    public SimpleTreeCellRenderer(RaplaResources i18n, RaplaLocale raplaLocale)
    {
        this.i18n = i18n;
        this.raplaLocale = raplaLocale;
        setFont(normalFont);
        setLeafIcon(null);
        setBorder(conflictBorder);
        defaultIcon = RaplaImages.getIcon(i18n.getIcon("icon.tree.default"));
        personIcon = RaplaImages.getIcon(i18n.getIcon("icon.tree.persons"));
        folderClosedIcon = RaplaImages.getIcon(i18n.getIcon("icon.folder"));
        folderOpenIcon = RaplaImages.getIcon(i18n.getIcon("icon.folder"));
    }

    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus)
    {
        Object nodeInfo = TreeFactoryImpl.getUserObject(value);

        setClosedIcon(folderClosedIcon);
        setOpenIcon(folderOpenIcon);
        setFont(normalFont);
        if (nodeInfo instanceof Allocatable)
        {
            Allocatable allocatable = (Allocatable) nodeInfo;
            Icon icon;
            if (allocatable.isPerson())
            {
                icon = personIcon;
            }
            else
            {
                icon = defaultIcon;
            }
            setClosedIcon(icon);
            setOpenIcon(icon);
        }
        String text = RaplaComponent.getName(nodeInfo, i18n.getLocale());
        Component result = super.getTreeCellRendererComponent(tree, text, sel, expanded, leaf, row, hasFocus);
        return result;
    }

}