/**
 *
 */
package org.rapla.client.menu.sandbox;

import org.rapla.RaplaResources;
import org.rapla.client.EditController;
import org.rapla.client.PopupContext;
import org.rapla.client.ReservationController;
import org.rapla.client.ReservationEdit;
import org.rapla.client.internal.RaplaClipboard;
import org.rapla.client.menu.sandbox.data.MenuCallback;
import org.rapla.client.menu.sandbox.data.MenuEntry;
import org.rapla.client.menu.sandbox.data.Point;
import org.rapla.components.calendarview.Block;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.plugin.abstractcalendar.RaplaBlock;
import org.rapla.scheduler.Promise;
import org.rapla.storage.PermissionController;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class CalendarContextMenuPresenter extends RaplaComponent implements MenuView.Presenter
{
    protected boolean keepTime = false;

    private final CalendarSelectionModel model;
    private final ReservationController reservationController;
    private final MenuView<?> view;

    private final PermissionController permissionController;
    ClientFacade clientFacade;
    private final EditController editController;

    //    private final InfoFactory infoFactory;

    //private final MenuFactory menuFactory;

    @Inject
    public CalendarContextMenuPresenter(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, CalendarSelectionModel model,
            ReservationController reservationController,  @SuppressWarnings("rawtypes") MenuView view, EditController editController)
    {
        super(facade.getRaplaFacade(), i18n, raplaLocale, logger);
        this.model = model;
        this.reservationController = reservationController;
        this.view = view;
        this.editController = editController;
        this.clientFacade = clientFacade;
        //        this.infoFactory = infoFactory;
      //  this.menuFactory = menuFactory;
        this.permissionController =facade.getRaplaFacade().getPermissionController();
    }

    private User getUser() throws RaplaException
    {
        return clientFacade.getUser();
    }

    protected final CalendarSelectionModel getModel()
    {
        return model;
    }

    /** override this method if you want to implement a custom time selection */
    public void selectionChanged(final Date start, final Date end)
    {
        TimeInterval interval = new TimeInterval(start, end);
        model.setMarkedIntervals(Collections.singleton(interval), !keepTime);
        Collection<Allocatable> markedAllocatables = getMarkedAllocatables();
        model.setMarkedAllocatables(markedAllocatables);
    }

    public void selectionPopup(final PopupContext popupContext)
    {
    }

    public void blockPopup(final Block block, final PopupContext popupContext)
    {
        RaplaBlock b = (RaplaBlock) block;
        if (!b.isBlockSelected())
        {
            return;
        }
        showPopupMenu(b, popupContext);
    }

    public void blockEdit(final Block block, final Point p)
    {
        // double click on block in view.
        RaplaBlock b = (RaplaBlock) block;
        if (!b.isBlockSelected())
        {
            return;
        }
        try
        {
            if (!permissionController.canModify(b.getReservation(), getUser()))
                return;
            final AppointmentBlock appointmentBlock = b.getAppointmentBlock();
            // FIXME convert point and component to popupContext
            PopupContext popupContext = null;
            editController.edit(appointmentBlock,popupContext);
        }
        catch (RaplaException ex)
        {
            view.showException(ex);
        }
    }

    public void moved(final Block block, Date newStart, int slotNr, final PopupContext popupContext)
    {
        moved(block, newStart, popupContext);
    }

    protected void moved(Block block, Date newStart, final PopupContext popupContext)
    {
        RaplaBlock b = (RaplaBlock) block;
        long offset = newStart.getTime() - b.getStart().getTime();
        Date newStartWithOffset = new Date(b.getAppointmentBlock().getStart() + offset);
        handleException(reservationController.moveAppointment(b.getAppointmentBlock(), newStartWithOffset, popupContext, keepTime));
    }

    public boolean isKeepTime()
    {
        return keepTime;
    }

    public void setKeepTime(boolean keepTime)
    {
        this.keepTime = keepTime;
    }

    public void resized(Block block, Point p, Date newStart, Date newEnd, int slotNr, final PopupContext popupContext)
    {
        RaplaBlock b = (RaplaBlock) block;
        handleException(reservationController.resizeAppointment(b.getAppointmentBlock(), newStart, newEnd, popupContext, keepTime));
    }

    private void handleException(Promise<Void> promise)
    {
        promise.exceptionally((ex)->view.showException(ex));
    }

    public Collection<Allocatable> getSelectedAllocatables()
    {
        try
        {
            return model.getSelectedAllocatablesAsList();
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

    protected void showPopupMenu(final RaplaBlock b, final PopupContext popupContext)
    {
    }

}