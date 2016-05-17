/**
 *
 */
package org.rapla.client.menu;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.rapla.RaplaResources;
import org.rapla.client.EditController;
import org.rapla.client.PopupContext;
import org.rapla.client.ReservationController;
import org.rapla.client.ReservationEdit;
import org.rapla.client.internal.RaplaClipboard;
import org.rapla.client.menu.data.MenuCallback;
import org.rapla.client.menu.data.MenuEntry;
import org.rapla.client.menu.data.Point;
import org.rapla.components.calendarview.Block;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.plugin.abstractcalendar.AbstractRaplaBlock;
import org.rapla.storage.PermissionController;

@Singleton
public class CalendarContextMenuPresenter extends RaplaComponent implements MenuView.Presenter
{
    protected boolean keepTime = false;

    private final CalendarSelectionModel model;
    private final ReservationController reservationController;
    private final MenuView<?> view;
    private final RaplaClipboard clipboard;

    private final PermissionController permissionController;
    ClientFacade clientFacade;
    private final EditController editController;

    //    private final InfoFactory infoFactory;

    //private final MenuFactory menuFactory;

    @Inject
    public CalendarContextMenuPresenter(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, CalendarSelectionModel model,
            ReservationController reservationController, RaplaClipboard clipboard/*,  InfoFactory infoFactory,
            MenuFactory menuFactory*/, @SuppressWarnings("rawtypes") MenuView view, EditController editController)
    {
        super(facade.getRaplaFacade(), i18n, raplaLocale, logger);
        this.model = model;
        this.reservationController = reservationController;
        this.view = view;
        this.clipboard = clipboard;
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
        try
        {
            final Map<MenuEntry, Runnable> mapping = new HashMap<MenuEntry, Runnable>();
            final List<MenuEntry> menu = new ArrayList<MenuEntry>();
            // Object focusedObject = null;
//             MenuContext context = new MenuContext(getContext(), focusedObject);
//            // TODO
//             menuFactory.addReservationWizards(new MenuInterface()
//            {
//                
//                @Override
//                public void removeAllBetween(String startId, String endId)
//                {
//                    
//                }
//                
//                @Override
//                public void removeAll()
//                {
//                    menu.clear();
//                }
//                
//                @Override
//                public void remove(RaplaAction item)
//                {
//                    
//                }
//                
//                @Override
//                public void insertBeforeId(JComponent component, String id)
//                {
//                    
//                }
//                
//                @Override
//                public void insertAfterId(ServerComponent component, String id)
//                {
//                    
//                }
//                
//                @Override
//                public void addSeparator()
//                {
//                    
//                }
//                
//                @Override
//                public void add(RaplaAction item)
//                {
//                    
//                }
//            }, context, null);
            final RaplaFacade raplaFacade = getFacade();
            final User user = getUser();
            if (permissionController.canCreateReservation(user))
            {
                if (permissionController.canUserAllocateSomething(user))
                {
                    ReservationEdit[] editWindows = editController.getEditWindows();
                    if (editWindows.length > 0)
                    {
                        final String text = getString("add_to");
                        MenuEntry addItem = new MenuEntry(text, null, true);
                        menu.add(addItem);
                        final List<MenuEntry> subEntries = addItem.getSubEntries();
                        for (final ReservationEdit reservationEdit : editWindows)
                        {
                            final String name2 = reservationEdit.getReservation().getName(getLocale());
                            String value = name2.trim().length() > 0 ? "'" + name2 + "'" : getString("new_reservation");
                            final MenuEntry reservationMenu = new MenuEntry(value, "icon.new", canAllocate());
                            subEntries.add(reservationMenu);
                            mapping.put(reservationMenu, new Runnable()
                            {
                                @Override
                                public void run()
                                {
                                    Date start = getStartDate(model,raplaFacade,user);
                                    Date end = getEndDate(model, start);
                                    try
                                    {
                                        reservationEdit.addAppointment(start, end);
                                    }
                                    catch (RaplaException e)
                                    {
                                        view.showException(e);
                                    }
                                }
                            });
                        }
                    }
                }
                else
                {
                    final String text = getString("permission.denied");
                    menu.add(new MenuEntry(text, null, false));
                }
            }
            Appointment appointment = clipboard.getAppointment();
            if (appointment != null)
            {
                if (clipboard.isPasteExistingPossible())
                {
                    final String text = getString("paste_into_existing_event");
                    final String icon = "icon.paste";
                    final boolean enabled = reservationController.isAppointmentOnClipboard() && permissionController.canCreateReservation(user);
                    final MenuEntry entry = new MenuEntry(text, icon, enabled);
                    menu.add(entry);
                    mapping.put(entry, new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            Date start = getStartDate(model, raplaFacade,user);
                            boolean keepTime = !model.isMarkedIntervalTimeEnabled();
                            try
                            {
                                reservationController.pasteAppointment(start, popupContext, false, keepTime);
                            }
                            catch (RaplaException e)
                            {
                                view.showException(e);
                            }
                        }
                    });
                }
                final String text = getString("paste_as") + " " + getString("new_reservation");
                final String icon = "icon.paste_new";
                final boolean enabled = reservationController.isAppointmentOnClipboard() && permissionController.canCreateReservation(user);
                final MenuEntry entry = new MenuEntry(text, icon, enabled);
                menu.add(entry);
                mapping.put(entry, new Runnable()
                {
                    @Override
                    public void run()
                    {
                        Date start = getStartDate(model, raplaFacade, user);
                        boolean keepTime = !model.isMarkedIntervalTimeEnabled();
                        try
                        {
                            reservationController.pasteAppointment(start, popupContext, true, keepTime);
                        }
                        catch (RaplaException e)
                        {
                            view.showException(e);
                        }
                    }
                });
            }
            view.showMenuPopup(menu, popupContext, new MenuCallback()
            {
                @Override
                public void selectEntry(MenuEntry entry)
                {
                    final Runnable action = mapping.get(entry);
                    if (action != null)
                    {
                        action.run();
                    }
                }
            });
        }
        catch (RaplaException ex)
        {
            view.showException(ex);
        }
    }

    public void blockPopup(final Block block, final PopupContext popupContext)
    {
        AbstractRaplaBlock b = (AbstractRaplaBlock) block;
        if (!b.isBlockSelected())
        {
            return;
        }
        showPopupMenu(b, popupContext);
    }

    public void blockEdit(final Block block, final Point p)
    {
        // double click on block in view.
        AbstractRaplaBlock b = (AbstractRaplaBlock) block;
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
        AbstractRaplaBlock b = (AbstractRaplaBlock) block;
        try
        {
            long offset = newStart.getTime() - b.getStart().getTime();
            Date newStartWithOffset = new Date(b.getAppointmentBlock().getStart() + offset);
            reservationController.moveAppointment(b.getAppointmentBlock(), newStartWithOffset, popupContext, keepTime);
        }
        catch (RaplaException ex)
        {
            view.showException(ex);
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

    public void resized(Block block, Point p, Date newStart, Date newEnd, int slotNr, final PopupContext popupContext)
    {
        AbstractRaplaBlock b = (AbstractRaplaBlock) block;
        try
        {
            reservationController.resizeAppointment(b.getAppointmentBlock(), newStart, newEnd, popupContext, keepTime);
        }
        catch (RaplaException ex)
        {
            view.showException(ex);
        }
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

    protected void showPopupMenu(final AbstractRaplaBlock b, final PopupContext popupContext)
    {
        final Map<MenuEntry, Runnable> mapping = new HashMap<MenuEntry, Runnable>();
        final AppointmentBlock appointmentBlock = b.getAppointmentBlock();
        final Appointment appointment = b.getAppointment();
        final Date start = b.getStart();
        final boolean isException = b.isException();
        final List<MenuEntry> menu = new ArrayList<MenuEntry>();
        final Allocatable groupAllocatable = b.getGroupAllocatable();

        final Collection<Allocatable> copyContextAllocatables;
        if (groupAllocatable != null)
        {
            copyContextAllocatables = Collections.singleton(groupAllocatable);
        }
        else
        {
            copyContextAllocatables = Collections.emptyList();
        }

        User user;
        try
        {
            user = getUser();
        }
        catch (RaplaException e1)
        {
            view.showException(e1);
            return;
        }
        {
            final String text = getString("copy");
            final String icon = "icon.copy";
            final boolean enabled = permissionController.canCreateReservation(user);
            final MenuEntry entry = new MenuEntry(text, icon, enabled);
            menu.add(entry);
            mapping.put(entry, new Runnable()
            {
                public void run()
                {
                    try
                    {
                        reservationController.copyAppointment(appointmentBlock, popupContext, copyContextAllocatables);
                    }
                    catch (RaplaException e)
                    {
                        view.showException(e);
                    }
                }
            });
        }
        {
            final String text = getString("cut");
            final String icon = "icon.cut";

            final boolean enabled = permissionController.canCreateReservation(user);
            final MenuEntry entry = new MenuEntry(text, icon, enabled);
            menu.add(entry);
            mapping.put(entry, new Runnable()
            {
                public void run()
                {
                    try
                    {
                        reservationController.cutAppointment(appointmentBlock, popupContext, copyContextAllocatables);
                    }
                    catch (RaplaException e)
                    {
                        view.showException(e);
                    }
                }
            });
        }
        {
            final String icon = "icon.edit";
            boolean canExchangeAllocatables = getQuery().canExchangeAllocatables(user,appointment.getReservation());
            boolean canModify = permissionController.canModify(appointment.getReservation(), user);
            String text = !canModify && canExchangeAllocatables ? getString("exchange_allocatables") : getString("edit");
            final boolean enabled = canModify || canExchangeAllocatables;
            final MenuEntry entry = new MenuEntry(text, icon, enabled);
            menu.add(entry);
            mapping.put(entry, new Runnable()
            {
                public void run()
                {
                    editController.edit(appointmentBlock, popupContext);
                }
            });
        }
        if (!isException)
        {
            final String text = getI18n().format("delete.format", getString("appointment"));
            final String icon = "icon.delete";
            final boolean enabled = permissionController.canModify(appointment.getReservation(), user);
            final MenuEntry entry = new MenuEntry(text, icon, enabled);
            menu.add(entry);
            mapping.put(entry, new Runnable()
            {
                public void run()
                {
                    try
                    {
                        reservationController.deleteAppointment(appointmentBlock, popupContext);
                    }
                    catch (RaplaException e)
                    {
                        view.showException(e);
                    }
                }
            });
        }
        {
            final String text = getString("view");
            final String icon = "icon.help";
            boolean enabled = permissionController.canRead(appointment, user);
            final MenuEntry entry = new MenuEntry(text, icon, enabled);
            menu.add(entry);
            mapping.put(entry, new Runnable()
            {
                public void run()
                {
                    //                    try
                    //                    {
                    //                        infoFactory.showInfoDialog(object, owner, point);
                    //                    }
                    //                    catch (RaplaException e)
                    //                    {
                    //                        view.showException(e);
                    //                    }
                }
            });
        }
        //
        //            Iterator<?> it = getContainer().lookupServicesFor(RaplaClientExtensionPoints.OBJECT_MENU_EXTENSION).iterator();
        //            while (it.hasNext())
        //            {
        //                ObjectMenuFactory objectMenuFact = (ObjectMenuFactory) it.next();
        //                MenuContext menuContext = new MenuContext(getContext(), appointment);
        //                menuContext.put(SELECTED_DATE, start);
        //
        //                RaplaMenuItem[] items = objectMenuFact.create(menuContext, appointment);
        //                for (int i = 0; i < items.length; i++)
        //                {
        //                    RaplaMenuItem item = items[i];
        //                    menu.add(item);
        //                }
        //            }

        view.showMenuPopup(menu, popupContext, new MenuCallback()
        {
            @Override
            public void selectEntry(MenuEntry entry)
            {
                final Runnable action = mapping.get(entry);
                if (action != null)
                {
                    action.run();
                }
            }
        });
    }

    // TODO DELETE
    private boolean canAllocate() throws RaplaException
    {
        //Date start, Date end,
        Collection<Allocatable> allocatables = model.getMarkedAllocatables();
        boolean canAllocate = true;
        Date start = getStartDate(model,getFacade(), getUser());
        Date end = getEndDate(model, start);
        for (Allocatable allo : allocatables)
        {
            if (!permissionController.canAllocate(start, end, allo, getUser()))
            {
                canAllocate = false;
            }
        }
        return canAllocate;
    }

}