package org.rapla.plugin.planningstatus.client;

import io.reactivex.rxjava3.functions.Consumer;
import org.apache.commons.io.file.Counters;
import org.rapla.RaplaResources;
import org.rapla.client.EditApplicationEventContext;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.event.ApplicationEvent;
import org.rapla.client.event.ApplicationEvent.ApplicationEventContext;
import org.rapla.client.event.ApplicationEventBus;
import org.rapla.client.extensionpoints.ObjectMenuFactory;
import org.rapla.client.internal.SaveUndo;
import org.rapla.client.internal.edit.EditTaskPresenter;
import org.rapla.client.menu.IdentifiableMenuEntry;
import org.rapla.client.menu.MenuItemFactory;
import org.rapla.client.menu.SelectionMenuContext;
import org.rapla.components.util.iterator.IteratorChain;
import org.rapla.components.util.undo.CommandHistory;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.inject.Extension;
import org.rapla.plugin.planningstatus.PlanningStatusFilter;
import org.rapla.plugin.planningstatus.PlanningStatusPlugin;
import org.rapla.plugin.planningstatus.PlanningStatusResources;
import org.rapla.scheduler.Promise;
import org.rapla.storage.PermissionController;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

@Singleton @Extension(provides = ObjectMenuFactory.class, id = "planningstatus") public class PlanningStatusMenuFactory implements ObjectMenuFactory
{
    private final RaplaResources i18n;
    private final PlanningStatusResources planningStatusResources;
    private final MenuItemFactory menuItemFactory;
    private final PermissionController permissionController;
    private final RaplaFacade raplaFacade;
    private final ClientFacade clientFacade;
    private final User user;
    private final boolean enabled;
    private final DialogUiFactoryInterface dialogUiFactory;

    @Inject public PlanningStatusMenuFactory(RaplaResources i18n, MenuItemFactory menuItemFactory, ClientFacade facade, PlanningStatusResources planningStatusResources,DialogUiFactoryInterface dialogUiFactory) throws RaplaInitializationException
    {
        this.i18n = i18n;
        this.dialogUiFactory = dialogUiFactory;
        this.planningStatusResources = planningStatusResources;
        this.menuItemFactory = menuItemFactory;
        try
        {
            user = facade.getUser();
            enabled = facade.getRaplaFacade().getSystemPreferences().getEntryAsBoolean(PlanningStatusPlugin.ENABLED, PlanningStatusPlugin.ENABLE_BY_DEFAULT);
        }
        catch (RaplaException e)
        {
            throw new RaplaInitializationException(e.getMessage(), e);
        }
        permissionController = facade.getRaplaFacade().getPermissionController();
        this.clientFacade = facade;
        this.raplaFacade = facade.getRaplaFacade();

    }

    @Override public IdentifiableMenuEntry[] create(final SelectionMenuContext menuContext, RaplaObject focusedObject)
    {
        if (!enabled)
        {
            return IdentifiableMenuEntry.EMPTY_ARRAY;
        }
        final Collection<?> selectedObjects = menuContext.getSelectedObjects();
        if (selectedObjects != null && selectedObjects.size() < 1 && focusedObject == null)
        {
            return IdentifiableMenuEntry.EMPTY_ARRAY;
        }
        Iterator it = selectedObjects.iterator();
        if ( focusedObject != null )
        {
            Iterator iterator = Collections.singletonList(focusedObject).iterator();
            it = new IteratorChain( it, iterator);
        }
        Collection<Reservation> reservationList = new LinkedHashSet<>();
        Collection<Reservation> complete = new LinkedHashSet<>();
        Collection<Reservation> uncomplete = new LinkedHashSet<>();
        while (it.hasNext())
        {
            final Object object = it.next();
            if (object instanceof AppointmentBlock || object instanceof Reservation || object instanceof Appointment)
            {
                final Reservation reservation;
                if (object instanceof  Reservation) {
                    reservation = (Reservation) object;
                } else if (object instanceof Appointment) {
                    reservation = ((Appointment) object).getReservation();
                } else {
                    reservation = ((AppointmentBlock) object).getAppointment().getReservation();
                }
                if ( !permissionController.canModify(reservation, user))
                {
                    return IdentifiableMenuEntry.EMPTY_ARRAY;
                }
                if (PlanningStatusFilter.isPlannable( reservation ))
                {
                    if (PlanningStatusFilter.testReservation(reservation)) {
                        complete.add(reservation);
                    } else {
                        uncomplete.add(reservation);
                    }
                    reservationList.add( reservation );
                }
            }
            else
            {
                return IdentifiableMenuEntry.EMPTY_ARRAY;
            }

        }
        if ( reservationList.isEmpty())
        {
            return IdentifiableMenuEntry.EMPTY_ARRAY;
        }


        IdentifiableMenuEntry completeEhtry;
        {
            String comandoName = planningStatusResources.getString("complete_planning");
            Consumer<PopupContext> action = (popupContext)
                    ->
            {
                changeChecked(uncomplete, true, popupContext, comandoName);
            };
            completeEhtry = menuItemFactory.createMenuItem(comandoName,i18n.getIcon("icon.checked"), action);
            completeEhtry.setEnabled( !uncomplete.isEmpty() );
        }

        IdentifiableMenuEntry uncompleteEhtry;
        {
            String commandoName = planningStatusResources.getString("uncomplete_planning");
            Consumer<PopupContext> action = (popupContext)
                    ->
            {
                changeChecked(complete, false, popupContext, commandoName);
            };
            uncompleteEhtry = menuItemFactory.createMenuItem(commandoName,i18n.getIcon("icon.unchecked"), action);
            uncompleteEhtry.setEnabled( !complete.isEmpty() );
        }
        IdentifiableMenuEntry[] menuItem = new IdentifiableMenuEntry[2];
        menuItem[0] = completeEhtry;
        menuItem[1] = uncompleteEhtry;
        return menuItem;
    }

    private void changeChecked(Collection<Reservation> events, boolean checked, PopupContext popupContext, String commandoName) throws RaplaException {
        Collection<Reservation> editable = raplaFacade.editList(events);
        Map<Reservation, Reservation> editMap = new HashMap<>();
        Iterator<Reservation> it = editable.iterator();
        for (Reservation reservation : events) {
            Reservation toEdit= it.next();
            Classification classification = toEdit.getClassification();
            Attribute status = classification.getAttribute("status");
            if (status == null || status.getType() != AttributeType.BOOLEAN) {
                continue;
            }
            classification.setValueForAttribute(status, checked ? Boolean.TRUE : Boolean.FALSE);
            editMap.put(reservation, toEdit);
        }
        SaveUndo<Reservation> save = new SaveUndo<>(raplaFacade, i18n, editMap, commandoName);
        Promise<Void> promise = clientFacade.getCommandHistory().storeAndExecute(save);
        promise.exceptionally(ex ->
            dialogUiFactory.showException(ex, popupContext)
        );
    }



}
