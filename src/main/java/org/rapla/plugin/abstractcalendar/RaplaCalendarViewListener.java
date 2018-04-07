/**
 *
 */
package org.rapla.plugin.abstractcalendar;

import org.rapla.RaplaResources;
import org.rapla.client.EditController;
import org.rapla.client.PopupContext;
import org.rapla.client.ReservationController;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.menu.MenuFactory;
import org.rapla.client.menu.SelectionMenuContext;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.toolkit.RaplaPopupMenu;
import org.rapla.components.calendarview.Block;
import org.rapla.components.calendarview.swing.ViewListener;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.domain.Allocatable;
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
import java.awt.Component;
import java.awt.Point;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class RaplaCalendarViewListener extends RaplaGUIComponent implements ViewListener
{
    protected boolean keepTime = false;

    protected JComponent calendarContainerComponent;
    CalendarModel model;
    private final MenuFactory menuFactory;
    private final ReservationController reservationController;
    private final DialogUiFactoryInterface dialogUiFactory;
    final PermissionController permissionController;
    final EditController editController;


    public RaplaCalendarViewListener(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, CalendarModel model, JComponent calendarContainerComponent,
             MenuFactory menuFactory, ReservationController reservationController,  DialogUiFactoryInterface dialogUiFactory, EditController editController)
    {
        super(facade, i18n, raplaLocale, logger);
        this.editController = editController;
        this.model = model;
        this.calendarContainerComponent = calendarContainerComponent;
        this.menuFactory = menuFactory;
        this.reservationController = reservationController;
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
            menuFactory.addCalendarSelectionMenu( menu,context);
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
        final PopupContext popupContext = createPopupContext(component, p);
        RaplaPopupMenu menu = new RaplaPopupMenu( popupContext);
        final SelectionMenuContext selectionMenuContext = new SelectionMenuContext(b, popupContext);
        menuFactory.addObjectMenu( menu, selectionMenuContext, null);
        menu.show(component, p.x, p.y);
    }


}