package org.rapla.plugin.merge.client.swing;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.ImageIcon;

import org.rapla.RaplaResources;
import org.rapla.client.EditApplicationEventContext;
import org.rapla.client.PopupContext;
import org.rapla.client.event.ApplicationEvent;
import org.rapla.client.event.ApplicationEvent.ApplicationEventContext;
import org.rapla.client.extensionpoints.ObjectMenuFactory;
import org.rapla.client.internal.edit.MergeActivity;
import org.rapla.client.swing.SwingMenuContext;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.toolkit.DialogUI.DialogUiFactory;
import org.rapla.client.swing.toolkit.RaplaMenuItem;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.inject.Extension;
import org.rapla.storage.PermissionController;

import com.google.web.bindery.event.shared.EventBus;

@Singleton @Extension(provides = ObjectMenuFactory.class, id = "merge") public class MergeMenuFactory implements ObjectMenuFactory
{
    private final RaplaImages raplaImages;
    private final RaplaResources raplaResources;
    private final DialogUiFactory dialogUiFactory;
    private final EventBus eventBus;
    //    private final MergeDialogFactory mergeDialogFactory;
    //    private final EditController editController;
    private final PermissionController permissionController;
    private final User user;

    @Inject public MergeMenuFactory(RaplaResources raplaResources, RaplaImages raplaImages, ClientFacade facade, DialogUiFactory dialogUiFactory,
            EventBus eventBus) throws RaplaInitializationException
    {
        this.raplaResources = raplaResources;
        this.raplaImages = raplaImages;
        try
        {
            user = facade.getUser();
        }
        catch (RaplaException e)
        {
            throw new RaplaInitializationException(e.getMessage(), e);
        }
        permissionController = facade.getRaplaFacade().getPermissionController();
        this.dialogUiFactory = dialogUiFactory;
        this.eventBus = eventBus;
    }

    @Override public RaplaMenuItem[] create(final SwingMenuContext menuContext, RaplaObject focusedObject)
    {
        final Collection<?> selectedObjects = menuContext.getSelectedObjects();
        if (selectedObjects != null && selectedObjects.size() <= 1)
        {
            return new RaplaMenuItem[0];
        }
        Iterator<?> it = selectedObjects.iterator();
        Object last = it.next();
        if (!(last instanceof Allocatable) || !permissionController.canAdmin((Allocatable) last, user))
        {
            return new RaplaMenuItem[0];
        }
        while (it.hasNext())
        {
            final Object next = it.next();
            if (!next.getClass().equals(last.getClass()))
            {
                return new RaplaMenuItem[0];
            }
            if (next instanceof Allocatable)
            {
                if (!((Allocatable) next).getClassification().getType().equals(((Allocatable) last).getClassification().getType()) || !permissionController
                        .canAdmin((Allocatable) next, user))
                {
                    return new RaplaMenuItem[0];
                }
            }
            else
            {
                return new RaplaMenuItem[0];
            }
        }
        RaplaMenuItem[] menuItem = new RaplaMenuItem[1];
        menuItem[0] = new RaplaMenuItem("merge");
        final String title = raplaResources.getString("merge");
        menuItem[0].setText(title);
        final ImageIcon icon = raplaImages.getIconFromKey("icon.merge");
        menuItem[0].setIcon(icon);
        menuItem[0].addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                final RaplaMenuItem e1 = (RaplaMenuItem) e.getSource();
                PopupContext popupContext = new SwingPopupContext(e1.getComponent(), null);
                StringBuilder ids = new StringBuilder();
                boolean first = true;
                for (Object object : selectedObjects)
                {
                    if (first)
                    {
                        first = false;
                    }
                    else
                    {
                        ids.append(",");
                    }
                    ids.append(((Entity) object).getId());
                }
                final String info = ids.toString();
                ApplicationEventContext context = new EditApplicationEventContext(new ArrayList( selectedObjects));
                eventBus.fireEvent(new ApplicationEvent(MergeActivity.ID, info, popupContext, context));
            }
        });

        return menuItem;
    }

}
