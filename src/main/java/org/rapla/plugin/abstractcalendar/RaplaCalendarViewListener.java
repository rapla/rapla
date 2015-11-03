/**
 *
 */
package org.rapla.plugin.abstractcalendar;

import java.awt.Component;
import java.awt.Point;
import java.util.*;

import javax.swing.JComponent;
import javax.swing.JMenuItem;

import org.rapla.client.ReservationController;
import org.rapla.client.ReservationEdit;
import org.rapla.client.internal.RaplaClipboard;
import org.rapla.components.calendarview.Block;
import org.rapla.components.calendarview.swing.ViewListener;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.NamedComparator;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.client.swing.InfoFactory;
import org.rapla.client.swing.MenuContext;
import org.rapla.client.swing.MenuFactory;
import org.rapla.client.extensionpoints.ObjectMenuFactory;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.action.AppointmentAction;
import org.rapla.client.swing.toolkit.DialogUI;
import org.rapla.client.swing.toolkit.DialogUI.DialogUiFactory;
import org.rapla.client.swing.toolkit.MenuInterface;
import org.rapla.client.swing.toolkit.RaplaMenu;
import org.rapla.client.swing.toolkit.RaplaMenuItem;
import org.rapla.client.swing.toolkit.RaplaPopupMenu;
import org.rapla.plugin.abstractcalendar.client.swing.SwingRaplaBlock;

public class RaplaCalendarViewListener extends RaplaGUIComponent implements ViewListener
{
    public static TypedComponentRole<Date> SELECTED_DATE = new TypedComponentRole<Date>(RaplaCalendarViewListener.class.getName());
    protected boolean keepTime = false;
    private final Set<ObjectMenuFactory> objectMenuFactories;

    protected JComponent calendarContainerComponent;
    CalendarModel model;
    private final MenuFactory menuFactory;
    private final CalendarSelectionModel calendarSelectionModel;
    private final RaplaClipboard clipboard;
    private final ReservationController reservationController;
    private final InfoFactory<Component, DialogUI> infoFactory;
    private final RaplaImages raplaImages;
    private final DialogUiFactory dialogUiFactory;

    public RaplaCalendarViewListener(RaplaContext context, CalendarModel model, JComponent calendarContainerComponent,
            Set<ObjectMenuFactory> objectMenuFactories, MenuFactory menuFactory, CalendarSelectionModel calendarSelectionModel, RaplaClipboard clipboard, ReservationController reservationController, InfoFactory<Component, DialogUI> infoFactory, RaplaImages raplaImages, DialogUiFactory dialogUiFactory)
    {
        super(context);
        this.model = model;
        this.calendarContainerComponent = calendarContainerComponent;
        this.objectMenuFactories = objectMenuFactories;
        this.menuFactory = menuFactory;
        this.calendarSelectionModel = calendarSelectionModel;
        this.clipboard = clipboard;
        this.reservationController = reservationController;
        this.infoFactory = infoFactory;
        this.raplaImages = raplaImages;
        this.dialogUiFactory = dialogUiFactory;
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
            RaplaPopupMenu menu = new RaplaPopupMenu();
            Object focusedObject = null;
            MenuContext context = new MenuContext(getContext(), focusedObject);
            menuFactory.addReservationWizards(menu, context, null);

            if (canCreateReservation())
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

                if (canUserAllocateSomething(getUser()))
                {
                    ReservationEdit[] editWindows = reservationController.getEditWindows();
                    if (editWindows.length > 0)
                    {
                        RaplaMenu addItem = new RaplaMenu("add_to");
                        addItem.setText(getString("add_to"));
                        menu.add(addItem);

                        for (ReservationEdit reservationEdit : editWindows)
                        {
                            addAppointmentAction(addItem, component, p).setAddTo(reservationEdit);
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
                    addAppointmentAction(menu, component, p).setPaste();
                }
                addAppointmentAction(menu, component, p).setPasteAsNew();
            }

            menu.show(component, p.x, p.y);
        }
        catch (RaplaException ex)
        {
            showException(ex, calendarContainerComponent, dialogUiFactory);
        }

    }

    public void blockPopup(Block block, Point p)
    {
        SwingRaplaBlock b = (SwingRaplaBlock) block;
        if (!b.getContext().isBlockSelected())
        {
            return;
        }
        showPopupMenu(b, p);
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
            if (!canModify(b.getReservation()))
                return;
            final AppointmentBlock appointmentBlock = b.getAppointmentBlock();
            reservationController.edit(appointmentBlock);
        }
        catch (RaplaException ex)
        {
            showException(ex, b.getView(), dialogUiFactory);
        }
    }

    public void moved(Block block, Point p, Date newStart, int slotNr)
    {
        moved(block, p, newStart);
    }

    protected void moved(Block block, Point p, Date newStart)
    {
        SwingRaplaBlock b = (SwingRaplaBlock) block;
        try
        {
            long offset = newStart.getTime() - b.getStart().getTime();
            Date newStartWithOffset = new Date(b.getAppointmentBlock().getStart() + offset);
            reservationController.moveAppointment(b.getAppointmentBlock(), newStartWithOffset, createPopupContext(calendarContainerComponent, p),
                    keepTime);
        }
        catch (RaplaException ex)
        {
            showException(ex, b.getView(), dialogUiFactory);
        }
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
        try
        {
            reservationController.resizeAppointment(b.getAppointmentBlock(), newStart, newEnd, createPopupContext(calendarContainerComponent, p),
                    keepTime);
        }
        catch (RaplaException ex)
        {
            showException(ex, b.getView(), dialogUiFactory);
        }
    }

    public List<Allocatable> getSortedAllocatables()
    {
        try
        {
            Allocatable[] selectedAllocatables;
            selectedAllocatables = model.getSelectedAllocatables();
            List<Allocatable> sortedAllocatables = new ArrayList<Allocatable>(Arrays.asList(selectedAllocatables));
            Collections.sort(sortedAllocatables, new NamedComparator<Allocatable>(getLocale()));
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
        List<Allocatable> selectedAllocatables = getSortedAllocatables();
        if (selectedAllocatables.size() == 1)
        {
            return Collections.singletonList(selectedAllocatables.get(0));
        }
        return Collections.emptyList();
    }

    protected void showPopupMenu(SwingRaplaBlock b, Point p)
    {
        Component component = b.getView();
        AppointmentBlock appointmentBlock = b.getAppointmentBlock();
        Appointment appointment = b.getAppointment();
        Date start = b.getStart();
        boolean isException = b.isException();
        RaplaPopupMenu menu = new RaplaPopupMenu();
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
        addAppointmentAction(menu, component, p).setCopy(appointmentBlock, copyContextAllocatables);
        addAppointmentAction(menu, component, p).setCut(appointmentBlock, copyContextAllocatables);
        addAppointmentAction(menu, component, p).setEdit(appointmentBlock);
        if (!isException)
        {
            addAppointmentAction(menu, component, p).setDelete(appointmentBlock);
        }
        addAppointmentAction(menu, component, p).setView(appointmentBlock);

        Iterator<ObjectMenuFactory> it = objectMenuFactories.iterator();
        while (it.hasNext())
        {
            ObjectMenuFactory objectMenuFact = it.next();
            MenuContext menuContext = new MenuContext(getContext(), appointment);
            menuContext.put(SELECTED_DATE, start);

            RaplaMenuItem[] items = objectMenuFact.create(menuContext, appointment);
            for (int i = 0; i < items.length; i++)
            {
                RaplaMenuItem item = items[i];
                menu.add(item);
            }
        }

        menu.show(component, p.x, p.y);
    }

    public AppointmentAction addAppointmentAction(MenuInterface menu, Component parent, Point p)
    {
        AppointmentAction action = new AppointmentAction(getContext(), createPopupContext(parent, p), calendarSelectionModel, reservationController, infoFactory, raplaImages, dialogUiFactory);
        menu.add(action);
        return action;
    }

}