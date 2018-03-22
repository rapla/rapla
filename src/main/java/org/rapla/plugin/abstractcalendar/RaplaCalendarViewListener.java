/**
 *
 */
package org.rapla.plugin.abstractcalendar;

import org.rapla.RaplaResources;
import org.rapla.client.EditController;
import org.rapla.client.PopupContext;
import org.rapla.client.ReservationController;
import org.rapla.client.ReservationEdit;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.dialog.InfoFactory;
import org.rapla.client.extensionpoints.ObjectMenuFactory;
import org.rapla.client.internal.RaplaClipboard;
import org.rapla.client.menu.IdentifiableMenuEntry;
import org.rapla.client.menu.MenuFactory;
import org.rapla.client.menu.MenuItemFactory;
import org.rapla.client.menu.SelectionMenuContext;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.internal.action.AppointmentAction;
import org.rapla.client.swing.toolkit.RaplaMenu;
import org.rapla.client.swing.toolkit.RaplaPopupMenu;
import org.rapla.components.calendarview.Block;
import org.rapla.components.calendarview.swing.ViewListener;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.plugin.abstractcalendar.client.swing.SwingRaplaBlock;
import org.rapla.scheduler.Promise;
import org.rapla.storage.PermissionController;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import java.awt.Component;
import java.awt.Point;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class RaplaCalendarViewListener extends RaplaGUIComponent implements ViewListener
{
    protected boolean keepTime = false;
    private final Set<ObjectMenuFactory> objectMenuFactories;

    protected JComponent calendarContainerComponent;
    CalendarModel model;
    private final MenuFactory menuFactory;
    private final CalendarSelectionModel calendarSelectionModel;
    private final RaplaClipboard clipboard;
    private final ReservationController reservationController;
    private final InfoFactory infoFactory;
    private final DialogUiFactoryInterface dialogUiFactory;
    final PermissionController permissionController;
    final EditController editController;


    public RaplaCalendarViewListener(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, CalendarModel model, JComponent calendarContainerComponent,
            Set<ObjectMenuFactory> objectMenuFactories, MenuFactory menuFactory, CalendarSelectionModel calendarSelectionModel, RaplaClipboard clipboard, ReservationController reservationController, InfoFactory infoFactory,  DialogUiFactoryInterface dialogUiFactory, EditController editController)
    {
        super(facade, i18n, raplaLocale, logger);
        this.editController = editController;
        this.model = model;
        this.calendarContainerComponent = calendarContainerComponent;
        this.objectMenuFactories = objectMenuFactories;
        this.menuFactory = menuFactory;
        this.calendarSelectionModel = calendarSelectionModel;
        this.clipboard = clipboard;
        this.reservationController = reservationController;
        this.infoFactory = infoFactory;
        this.dialogUiFactory = dialogUiFactory;
        permissionController = facade.getRaplaFacade().getPermissionController();
    }

    protected CalendarModel getModel()
    {
        return model;
    }

    /** override this method if you want to implement a custom time selection */
    public void selectionChanged(Date start, Date end)
    {
        // #TODO this cast need to be replaced without adding the setter methods to the readOnly interface CalendarModel
        CalendarSelectionModel castedModel = (CalendarSelectionModel) model;
        TimeInterval interval = new TimeInterval(start, end);
        castedModel.setMarkedIntervals(Collections.singleton(interval), !keepTime);
        Collection<Allocatable> markedAllocatables = getMarkedAllocatables();
        castedModel.setMarkedAllocatables(markedAllocatables);
    }

    /** 
     * start, end and slotNr are not used because they are handled by selectionChanged method
     * @param start not used because handled by selectionChanged method
     * @param end not used because handled by selectionChanged method
     * @param slotNr not used because handled by selectionChanged method
     * 
     */
    public void selectionPopup(Component component, Point p, Date start, Date end, int slotNr)
    {
        selectionPopup(component, p);
    }

    public void selectionPopup(Component component, Point p)
    {
        try
        {
            final PopupContext popupContext = createPopupContext(component, p);
            RaplaPopupMenu menu = new RaplaPopupMenu(popupContext);
            Object focusedObject = null;
            SelectionMenuContext context = new SelectionMenuContext( focusedObject,popupContext);
            menuFactory.addReservationWizards(menu, context, null);
            MenuItemFactory f = menuFactory.getItemFactory();
            User user = getUser();
            if (permissionController.canCreateReservation(user))
            {
                //	        	 User user = getUserFromRequest();
                //	 	        Date today = getQuery().today();
                //	 	        boolean canAllocate = false;
                //	 	        Collection<Allocatable> selectedAllocatables = getMarkedAllocatables();
                //	 	        for ( Allocatable alloc: selectedAllocatables) {
                //	 	            if (alloc.canAllocate( user, start, end, today))
                //	 	                canAllocate = true;
                //	 	        }
                //	 	       canAllocate || (selectedAllocatables.size() == 0 &&

                if (permissionController.canUserAllocateSomething(user))
                {
                    ReservationEdit[] editWindows = editController.getEditWindows();
                    if (editWindows.length > 0)
                    {
                        RaplaMenu addItem = new RaplaMenu("add_to");
                        addItem.setText(getString("add_to"));
                        menu.add(addItem);
                        for (ReservationEdit reservationEdit : editWindows)
                        {
                            createAction( popupContext).setAddTo(reservationEdit).addTo( menu, f);
                        }
                    }
                }
                else
                {
                    JMenuItem cantAllocate = new JMenuItem(getString("permission.denied"));
                    cantAllocate.setEnabled(false);
                    menu.add(cantAllocate);
                }
            }
            //	
            Appointment appointment = clipboard.getAppointment();
            if (appointment != null)
            {
                if (clipboard.isPasteExistingPossible())
                {
                    createAction( popupContext).setPaste().addTo(menu, f );
                }
                createAction(popupContext).setPasteAsNew().addTo(menu, f);
            }

            menu.show(component, p.x, p.y);
        }
        catch (RaplaException ex)
        {
            PopupContext popupContext = dialogUiFactory.createPopupContext( ()->calendarContainerComponent);
            dialogUiFactory.showException(ex, popupContext);
        }

    }

    public void blockPopup(Block block, Point p)
    {
        SwingRaplaBlock b = (SwingRaplaBlock) block;
        if (!b.getContext().isBlockSelected())
        {
            return;
        }
        try
        {
            showPopupMenu(b, p);
        }
        catch(RaplaException e)
        {
            dialogUiFactory.showException(e, null);
        }
    }

    public void blockEdit(Block block, Point p)
    {
        // double click on block in view.
        SwingRaplaBlock b = (SwingRaplaBlock) block;
        if (!b.getContext().isBlockSelected())
        {
            return;
        }
        try
        {
            if (!permissionController.canModify(b.getReservation(), getUser()))
                return;
            final AppointmentBlock appointmentBlock = b.getAppointmentBlock();
            editController.edit(appointmentBlock,new SwingPopupContext(b.getView(), null));
        }
        catch (RaplaException ex)
        {
            dialogUiFactory.showException(ex, new SwingPopupContext(b.getView(), null));
        }
    }

    public void moved(Block block, Point p, Date newStart, int slotNr)
    {
        handleException(moved(block, p, newStart));
    }

    protected Promise<Void> moved(Block block, Point p, Date newStart)
    {
        SwingRaplaBlock b = (SwingRaplaBlock) block;
        final PopupContext popupContext = getPopupContext(p);
        long offset = newStart.getTime() - b.getStart().getTime();
        Date newStartWithOffset = new Date(b.getAppointmentBlock().getStart() + offset);
        return reservationController.moveAppointment(b.getAppointmentBlock(), newStartWithOffset, popupContext,
                keepTime);

    }

    protected void handleException(Promise<Void> promise)
    {
        final PopupContext popupContext = getPopupContext(null);
        promise.exceptionally((ex)->dialogUiFactory.showException(ex, popupContext));
    }

    private PopupContext getPopupContext(Point p) {
        return createPopupContext(calendarContainerComponent, p);
    }

    public boolean isKeepTime()
    {
        return keepTime;
    }

    public void setKeepTime(boolean keepTime)
    {
        this.keepTime = keepTime;
    }

    public void resized(Block block, Point p, Date newStart, Date newEnd, int slotNr)
    {
        SwingRaplaBlock b = (SwingRaplaBlock) block;
         handleException(reservationController.resizeAppointment(b.getAppointmentBlock(), newStart, newEnd, getPopupContext(p), keepTime));
    }

    public List<Allocatable> getSortedAllocatables()
    {
        try
        {
            List<Allocatable> sortedAllocatables = model.getSelectedAllocatablesSorted();
            return sortedAllocatables;
        }
        catch (RaplaException e)
        {
            getLogger().error(e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    public Collection<Allocatable> getSelectedAllocatables()
    {
        try
        {
            Collection<Allocatable> sortedAllocatables = model.getSelectedAllocatablesAsList();
            return sortedAllocatables;
        }
        catch (RaplaException e)
        {
            getLogger().error(e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /** override this method if you want to implement a custom allocatable marker */
    protected Collection<Allocatable> getMarkedAllocatables()
    {
        Collection<Allocatable> selectedAllocatables = getSelectedAllocatables();
        if (selectedAllocatables.size() == 1)
        {
            return Collections.singletonList(selectedAllocatables.iterator().next());
        }
        return Collections.emptyList();
    }

    protected void showPopupMenu(SwingRaplaBlock b, Point p) throws RaplaException
    {
        Component component = b.getView();
        AppointmentBlock appointmentBlock = b.getAppointmentBlock();
        Appointment appointment = b.getAppointment();
        Date start = b.getStart();
        boolean isException = b.isException();
        final PopupContext popupContext = createPopupContext(component, p);
        RaplaPopupMenu menu = new RaplaPopupMenu( popupContext);
        Allocatable groupAllocatable = b.getGroupAllocatable();

        Collection<Allocatable> copyContextAllocatables;
        if (groupAllocatable != null)
        {
            copyContextAllocatables = Collections.singleton(groupAllocatable);
        }
        else
        {
            copyContextAllocatables = Collections.emptyList();
        }

        MenuItemFactory f = menuFactory.getItemFactory();
        createAction(popupContext).setCopy(appointmentBlock, copyContextAllocatables).addTo(menu, f);
        createAction(popupContext).setCut(appointmentBlock, copyContextAllocatables).addTo( menu, f);
        createAction(popupContext).setEdit(appointmentBlock).addTo( menu, f);
        if (!isException)
        {
            createAction( popupContext).setDelete(appointmentBlock).addTo( menu, f);
        }
        createAction( popupContext).setView(appointmentBlock).addTo( menu, f);

        Iterator<ObjectMenuFactory> it = objectMenuFactories.iterator();
        while (it.hasNext())
        {
            ObjectMenuFactory objectMenuFact = it.next();
            SelectionMenuContext menuContext = new SelectionMenuContext( appointment,popupContext);
            menuContext.setSelectedDate( start);
            IdentifiableMenuEntry[] items = objectMenuFact.create(menuContext, appointment);
            for (IdentifiableMenuEntry item:items)
            {
                menu.addMenuItem(item);
            }
        }

        menu.show(component, p.x, p.y);
    }

    public AppointmentAction createAction(PopupContext popupContext)
    {
        AppointmentAction action = new AppointmentAction(getClientFacade(), getI18n(), getRaplaLocale(), getLogger(),popupContext, calendarSelectionModel, reservationController,editController, infoFactory, dialogUiFactory);
        return action;
    }

}