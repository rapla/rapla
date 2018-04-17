package org.rapla.client.swing.internal.view;

import org.rapla.RaplaResources;
import org.rapla.client.RaplaTreeNode;
import org.rapla.client.internal.TreeFactoryImpl;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.components.util.DateTools;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.facade.Conflict;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaLocale;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;
import java.util.Date;

public class ConflictTreeCellRenderer extends DefaultTreeCellRenderer {
    private final RaplaResources i18n;
    private final RaplaLocale raplaLocale;
    Font normalFont = UIManager.getFont("Tree.font");
    Font bigFont = normalFont.deriveFont(Font.BOLD, (float) (normalFont.getSize() * 1.2));

    Icon bigFolderConflicts;
    Icon folderClosedIcon;
    Icon folderOpenIcon;
    Icon defaultIcon;
    Icon personIcon;
    private static final long serialVersionUID = 1L;
    Border conflictBorder = BorderFactory.createEmptyBorder(2, 0, 2, 0);


    @Inject
    public ConflictTreeCellRenderer(RaplaResources i18n, RaplaLocale raplaLocale) {
        this.i18n = i18n;
        this.raplaLocale = raplaLocale;
        setFont(normalFont);
        setLeafIcon(null);
        setBorder(conflictBorder);
        bigFolderConflicts = RaplaImages.getIcon(i18n.getIcon("icon.big_folder_conflicts"));
        defaultIcon = RaplaImages.getIcon(i18n.getIcon("icon.tree.default"));
        personIcon = RaplaImages.getIcon(i18n.getIcon("icon.tree.persons"));
        folderClosedIcon = RaplaImages.getIcon(i18n.getIcon("icon.folder"));
        folderOpenIcon = RaplaImages.getIcon(i18n.getIcon("icon.folder"));
    }

    protected String getConflictText(Conflict conflict) {
        StringBuilder buf = new StringBuilder();
        Date startDate = conflict.getStartDate();
        buf.append(raplaLocale.formatDate(startDate));
        if (!DateTools.cutDate(startDate).equals(startDate)) {
            buf.append(' ');
            buf.append(raplaLocale.formatTime(startDate));
        }
        buf.append("<br>");
        buf.append(conflict.getReservation1Name());
        if (conflict.getRepeatingType1() != null) {
            buf.append(getRepeatingType(conflict.getRepeatingType1()));
        }
        buf.append(' ');
        buf.append(i18n.getString("with"));
        buf.append('\n');
        buf.append("<br>");
        buf.append(conflict.getReservation2Name());
        if (conflict.getRepeatingType2() != null) {
            buf.append(getRepeatingType(conflict.getRepeatingType2()));
        }
        String result = buf.toString();
        return result;
    }

    private String getRepeatingType(RepeatingType repeatingType) {
        final String keyName = repeatingType.name().toLowerCase();
        final String reslt = i18n.getString(keyName);
        return " [" + reslt + "]";
    }

    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        Object nodeInfo = TreeFactoryImpl.getUserObject(value);
        if (nodeInfo instanceof TreeFactoryImpl.ConflictRoot) {
            setFont(bigFont);
            value = value.toString();
            setIcon(bigFolderConflicts);
            setClosedIcon(bigFolderConflicts);
            setOpenIcon(bigFolderConflicts);
            leaf = false;
        } else {
            setClosedIcon(folderClosedIcon);
            setOpenIcon(folderOpenIcon);
            setFont(normalFont);
            if (nodeInfo instanceof Conflict) {
                Conflict conflict = (Conflict) nodeInfo;
                String text = "<html>" + getConflictText(conflict) + "</html>";
                value = text;
            } else {
                if (nodeInfo instanceof Allocatable) {
                    Allocatable allocatable = (Allocatable) nodeInfo;
                    Icon icon;
                    if (allocatable.isPerson()) {
                        icon = personIcon;
                    } else {
                        icon = defaultIcon;
                    }
                    setClosedIcon(icon);
                    setOpenIcon(icon);
                }

                String text = RaplaComponent.getName(nodeInfo,i18n.getLocale());
                if (value instanceof RaplaTreeNode) {
                    text += " (" + RaplaTreeNode.getRecursiveCount(((RaplaTreeNode) value), (obj) -> obj instanceof Conflict) + ")";
                }
                value = text;
            }
        }
        Component result = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
        return result;
    }
}
