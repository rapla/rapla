package org.rapla.client.swing.internal.view;

import org.rapla.RaplaResources;
import org.rapla.client.internal.TreeFactoryImpl;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.facade.internal.CalendarModelImpl;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;
import java.util.Date;

@DefaultImplementation(of= TreeCellRenderer.class,context = InjectionContext.swing)
public class ComplexTreeCellRenderer extends DefaultTreeCellRenderer {
    Icon bigFolderResourcesFiltered;
    Icon bigFolderResourcesUnfiltered;
    Icon bigFolderCategories;
    Icon defaultIcon;
    Icon personIcon;
    Icon folderClosedIcon;
    Icon folderOpenIcon;
    Icon forbiddenIcon;
    Font normalFont = UIManager.getFont("Tree.font");
    Font bigFont = normalFont.deriveFont(Font.BOLD, (float) (normalFont.getSize() * 1.2));
    private final RaplaFacade raplaFacade;
    private final ClientFacade clientFacade;
    private boolean filtered;

    private static final long serialVersionUID = 1L;

    Border nonIconBorder = BorderFactory.createEmptyBorder(1, 0, 1, 0);
    Border conflictBorder = BorderFactory.createEmptyBorder(2, 0, 2, 0);
    RaplaResources i18n;

    @Inject
    public ComplexTreeCellRenderer(RaplaResources i18n, RaplaFacade raplaFacade, ClientFacade clientFacade) {
        this.raplaFacade = raplaFacade;
        this.i18n = i18n;
        this.clientFacade = clientFacade;
        setLeafIcon(defaultIcon);
        bigFolderResourcesFiltered = RaplaImages.getIcon(i18n.getIcon("icon.big_folder_resources_filtered"));
        bigFolderResourcesUnfiltered = RaplaImages.getIcon(i18n.getIcon("icon.big_folder_resources"));
        bigFolderCategories = RaplaImages.getIcon(i18n.getIcon("icon.big_folder_categories"));
        defaultIcon = RaplaImages.getIcon(i18n.getIcon("icon.tree.default"));
        personIcon = RaplaImages.getIcon(i18n.getIcon("icon.tree.persons"));
        folderClosedIcon = RaplaImages.getIcon(i18n.getIcon("icon.folder"));
        folderOpenIcon = RaplaImages.getIcon(i18n.getIcon("icon.folder"));
        forbiddenIcon = RaplaImages.getIcon(i18n.getIcon("icon.no_perm"));
    }

    private void setIcon(Object object, boolean leaf) {
        Icon icon = null;
        boolean isAllocatable = false;
        if (object instanceof Allocatable) {
            isAllocatable = true;
            Allocatable allocatable = (Allocatable) object;
            try {
                User user = clientFacade.getUser();
                Date today = raplaFacade.today();
                if (!raplaFacade.getPermissionController().canAllocate(allocatable, user, today)) {
                    icon = forbiddenIcon;
                } else {
                    if (allocatable.isPerson()) {
                        icon = personIcon;
                    } else {
                        icon = defaultIcon;
                    }
                }
            } catch (RaplaException ex) {
            }

        } else if (object instanceof DynamicType) {
            DynamicType type = (DynamicType) object;
            String classificationType = type.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
            if (DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION.equals(classificationType)) {
                setBorder(conflictBorder);
            } else {
                icon = folderClosedIcon;
            }
        }
        if (icon == null) {
            setBorder(nonIconBorder);
        }
        if (leaf) {
            setLeafIcon(icon);
        } else if (isAllocatable) {
            setOpenIcon(icon);
            setClosedIcon(icon);
            setIcon(icon);
        }
    }

    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        setBorder(null);
        setFont(normalFont);
        Object nodeInfo = TreeFactoryImpl.getUserObject(value);
        if ( nodeInfo == CalendarModelImpl.ALLOCATABLES_ROOT) {
            Icon bigFolderIcon;
            if (filtered) {
                bigFolderIcon = bigFolderResourcesFiltered;
            } else {
                bigFolderIcon = bigFolderResourcesUnfiltered;
            }
            setClosedIcon(bigFolderIcon);
            setOpenIcon(bigFolderIcon);
            setLeafIcon(bigFolderIcon);
            setFont(bigFont);
            value = i18n.getString("resources");
        } else {

            setClosedIcon(folderClosedIcon);
            setOpenIcon(folderOpenIcon);
            //if (leaf) {
            setIcon(nodeInfo, leaf);
            //}
        }
        Component result = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
        return result;
    }

    public boolean isFiltered()
    {
        return filtered;
    }

    public void setFiltered(boolean filtered)
    {
        this.filtered = filtered;
    }
}
