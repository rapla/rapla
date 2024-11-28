/*--------------------------------------------------------------------------* | Copyright (C) 2008  Christopher Kohlhaas                                 |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/

package org.rapla.client.internal;

import org.rapla.client.PopupContext;
import org.rapla.client.RaplaWidget;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.event.CalendarEventBus;
import org.rapla.client.event.CalendarRefreshEvent;
import org.rapla.components.util.TimeInterval;
import org.rapla.components.util.undo.CommandHistory;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.Conflict;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.logger.Logger;
import org.rapla.scheduler.Promise;

import javax.inject.Inject;
import java.util.*;

public class RequestSelectionPresenter implements ResourceRequestSelectionView.Presenter
{
    protected final CalendarSelectionModel model;
    private final Logger logger;
    private Collection<Reservation> requests = Collections.emptySet();
    private final CalendarEventBus eventBus;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final ClientFacade facade;
    private final RaplaFacade raplaFacade;
    private final ResourceRequestSelectionView<?> view;
    private PresenterChangeCallback callback;

    @Inject
    public RequestSelectionPresenter(ClientFacade facade, Logger logger, final CalendarSelectionModel model, CalendarEventBus eventBus,
                                     DialogUiFactoryInterface dialogUiFactory, ResourceRequestSelectionView view) throws RaplaInitializationException
    {
        this.facade = facade;
        this.logger = logger;
        this.model = model;
        this.eventBus = eventBus;
        this.dialogUiFactory = dialogUiFactory;
        this.view = view;
        raplaFacade = facade.getRaplaFacade();
        view.setPresenter(this);
        if (raplaFacade.canAdminResourceRequests()) {
            queryAllRequests();
        }
    }

    @Override
    public void showRequests(PopupContext context)
    {
        Collection<Reservation> selectedRequests = getSelectedRequests();
        showRequests(selectedRequests);
    }

    public void setCallback(PresenterChangeCallback callback)
    {
        this.callback = callback;
    }

    @Override
    public void showTreePopup(PopupContext c)
    {
        try
        {
            // Object obj = evt.getSelected();
            Collection<?> list = view.getSelectedElements(true);

            //view.showMenuPopup(c, !disabledConflicts.isEmpty(), !enabledConflicts.isEmpty());
        }
        catch (Exception ex)
        {
            dialogUiFactory.showException(ex, null);
        }
    }

    protected Promise handleException(Promise promise, PopupContext context)
    {
        return promise.exceptionally(ex ->
            dialogUiFactory.showException((Throwable) ex, context)
        );
    }

    public CommandHistory getCommandHistory()
    {
        return facade.getCommandHistory();
    }


    protected CalendarSelectionModel getModel()
    {
        return model;
    }

    public void dataChanged(ModificationEvent evt) throws RaplaException
    {
        if (evt == null)
        {
            return;
        }
        TimeInterval invalidateInterval = evt.getInvalidateInterval();
        if (invalidateInterval != null && invalidateInterval.getStart() == null)
        {
            queryAllRequests();
        }
        else if (evt.isModified())
        {
            Set<Reservation> changed = RaplaType.retainObjects(evt.getChanged(), requests);
            removeAll(requests, changed);

            removeRequests(requests, evt.getRemovedReferences());
            User user = facade.getUser();

            for ( Reservation reservation : changed)
            {
                if ( raplaFacade.getPermissionController().isRequestFor(reservation, user))  {
                    requests.add(reservation);
                }
            }
            for (RaplaObject obj : evt.getAddObjects())
            {
                if (obj.getTypeClass() == Reservation.class)
                {
                    Reservation reservation = (Reservation) obj;
                    requests.add(reservation);
                }
            }
            updateTree(requests);
        }
        else
        {
            view.redraw();
        }
    }

    protected void queryAllRequests()  {
        raplaFacade.getResourceRequests()
                   .thenAccept(requests->updateTree(requests))
                   .exceptionally(ex -> {
                       logger.error(ex.getMessage(), ex);
                   });
    }

    private void removeRequests(Collection<Reservation> requests, Set<ReferenceInfo> removedReferences)
    {
        Set<String> removedIds = new LinkedHashSet<>();
        for (ReferenceInfo removedReference : removedReferences)
        {
            removedIds.add(removedReference.getId());
        }
        Iterator<Reservation> it = requests.iterator();
        while (it.hasNext())
        {
            if (removedIds.contains(it.next().getId()))
            {
                it.remove();
            }
        }
    }

    private void removeAll(Collection<Reservation> list, Set<Reservation> changed)
    {
        Iterator<Reservation> it = list.iterator();
        while (it.hasNext())
        {
            if (changed.contains(it.next()))
            {
                it.remove();
            }
        }

    }

    private void showRequests(Collection<Reservation> selectedRequests)
    {
        ArrayList<RaplaObject> arrayList = new ArrayList<>(model.getSelectedObjects());
        for (Iterator<RaplaObject> it = arrayList.iterator(); it.hasNext(); )
        {
            RaplaObject obj = it.next();
            if (obj.getTypeClass() == Reservation.class)
            {
                it.remove();
            }
        }
        arrayList.addAll(selectedRequests);
        model.setSelectedObjects(arrayList);
        if (!selectedRequests.isEmpty())
        {
            Collection<Appointment> requestedAppointments = ReservationImpl.getRequestedAppointments(selectedRequests);
            if ( !requestedAppointments.isEmpty()) {
                Date date = requestedAppointments.iterator().next().getStart();
                if (date != null) {
                    model.setSelectedDate(date);
                }
            }
        }
        eventBus.publish(new CalendarRefreshEvent());
    }

    private Collection<Reservation> getSelectedRequestsInModel()
    {
        Set<Reservation> result = new LinkedHashSet<>();
        for (RaplaObject obj : model.getSelectedObjects())
        {
            if (obj.getTypeClass() == Reservation.class)
            {
                result.add((Reservation) obj);
            }
        }
        return result;
    }

    private Collection<Reservation> getSelectedRequests()
    {
        Collection<Object> lastSelected = view.getSelectedElements(false);
        Set<Reservation> selectedRequests = new LinkedHashSet<>();
        for (Object selected : lastSelected)
        {
            if (selected instanceof Reservation)
            {
                selectedRequests.add((Reservation) selected);
            }
        }
        return selectedRequests;
    }

    private void updateTree(Collection<Reservation> newRequests)
    {
        this.requests = newRequests;
        Collection<Reservation> selectedRequests = new ArrayList<>(getSelectedRequests());
        Collection<Reservation> requests = getRequests();
        view.updateTree(selectedRequests, requests);
        Collection<Reservation> inModel = new ArrayList<>(getSelectedRequestsInModel());
        if (!selectedRequests.equals(inModel))
        {
            showRequests(inModel);
        }
    }

    public Collection<Reservation> getRequests()
    {
        List<Reservation> result = new ArrayList<>(requests);
        // FIXME: sort reservations by some means
        Collections.sort(result, new RequestStartDateComparator());
        return result;
    }

    class RequestStartDateComparator implements Comparator<Reservation>
    {
        public int compare(Reservation c1, Reservation c2)
        {
            if (c1.equals(c2))
            {
                return 0;
            }
            Date d1 = c1.getFirstDate();
            Date d2 = c2.getFirstDate();
            if (d1 != null)
            {
                if (d2 == null)
                {
                    return -1;
                }
                else
                {
                    int result = d1.compareTo(d2);
                    return result;
                }
            }
            else if (d2 != null)
            {
                return 1;
            }
            return Integer.valueOf(c1.hashCode()).compareTo(Integer.valueOf(c2.hashCode()));
        }

    }

    public RaplaWidget<?> getSummaryComponent()
    {
        return () -> view.getSummary();
    }

    public RaplaWidget<?> getRequestView()
    {
        return view;
    }

    public void clearSelection()
    {
        view.clearSelection();
    }

    @Override
    public void treeSelectionChanged()
    {
        callback.onChange();
    }

}
