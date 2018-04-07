package org.rapla.plugin.merge.client;

import io.reactivex.functions.Consumer;
import org.rapla.RaplaResources;
import org.rapla.client.EditApplicationEventContext;
import org.rapla.client.PopupContext;
import org.rapla.client.event.ApplicationEvent;
import org.rapla.client.event.ApplicationEvent.ApplicationEventContext;
import org.rapla.client.event.ApplicationEventBus;
import org.rapla.client.extensionpoints.ObjectMenuFactory;
import org.rapla.client.internal.edit.EditTaskPresenter;
import org.rapla.client.menu.IdentifiableMenuEntry;
import org.rapla.client.menu.MenuItemFactory;
import org.rapla.client.menu.SelectionMenuContext;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.inject.Extension;
import org.rapla.storage.PermissionController;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

@Singleton @Extension(provides = ObjectMenuFactory.class, id = "merge") public class MergeMenuFactory implements ObjectMenuFactory
{
    private final RaplaResources i18n;
    private final MenuItemFactory menuItemFactory;
    private final ApplicationEventBus eventBus;
    private final PermissionController permissionController;
    private final User user;

    @Inject public MergeMenuFactory(RaplaResources i18n, MenuItemFactory menuItemFactory, ClientFacade facade,
                                    ApplicationEventBus eventBus) throws RaplaInitializationException
    {
        this.i18n = i18n;
        this.menuItemFactory = menuItemFactory;
        try
        {
            user = facade.getUser();
        }
        catch (RaplaException e)
        {
            throw new RaplaInitializationException(e.getMessage(), e);
        }
        permissionController = facade.getRaplaFacade().getPermissionController();
        this.eventBus = eventBus;
    }

    @Override public IdentifiableMenuEntry[] create(final SelectionMenuContext menuContext, RaplaObject focusedObject)
    {
        final Collection<?> selectedObjects = menuContext.getSelectedObjects();
        if (selectedObjects != null && selectedObjects.size() <= 1)
        {
            return new IdentifiableMenuEntry[0];
        }
        if ( !canAdmin(selectedObjects))
        {
            return new IdentifiableMenuEntry[0];
        }
        Iterator<?> it = selectedObjects.iterator();
        Object last = it.next();
        while (it.hasNext())
        {
            final Object next = it.next();
            if (!next.getClass().equals(last.getClass()))
            {
                return new IdentifiableMenuEntry[0];
            }
            if (next instanceof Allocatable)
            {
                if (!((Allocatable) next).getClassification().getType().equals(((Allocatable) last).getClassification().getType()) || !permissionController
                        .canAdmin((Allocatable) next, user))
                {
                    return new IdentifiableMenuEntry[0];
                }
            }
            else
            {
                return new IdentifiableMenuEntry[0];
            }
        }
        IdentifiableMenuEntry[] menuItem = new IdentifiableMenuEntry[1];
        Consumer<PopupContext> action = (popupContext)
                ->
        {
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
            eventBus.publish(new ApplicationEvent(EditTaskPresenter.MERGE_RESOURCES_ID, info, popupContext, context));
        };
        menuItem[0] = menuItemFactory.createMenuItem(i18n.getString("merge"),i18n.getIcon("icon.merge"), action);
        return menuItem;
    }

    private boolean canAdmin(Collection<?> selectedObjects) {
        for ( Object last:selectedObjects) {
            if (!(last instanceof Allocatable) || !permissionController.canAdmin((Allocatable) last, user))
            {
                return false;
            }
        }
        return true;
    }

}
