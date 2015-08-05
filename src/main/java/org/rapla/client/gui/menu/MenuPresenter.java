/**
 *
 */
package org.rapla.client.gui.menu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import org.rapla.client.gui.menu.MenuView.Presenter;
import org.rapla.client.gui.menu.data.MenuCallback;
import org.rapla.client.gui.menu.data.MenuEntry;
import org.rapla.client.gui.menu.data.Point;
import org.rapla.components.calendarview.Block;
import org.rapla.components.util.TimeInterval;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.NamedComparator;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.gui.PopupContext;
import org.rapla.gui.ReservationController;
import org.rapla.gui.ReservationEdit;
import org.rapla.gui.internal.common.RaplaClipboard;
import org.rapla.plugin.abstractcalendar.AbstractRaplaBlock;

public class MenuPresenter extends RaplaComponent implements Presenter
{
    protected boolean keepTime = false;

    private final CalendarSelectionModel model;
    private final ReservationController reservationController;
    private final MenuView<?> view;
    private final RaplaClipboard clipboard;

    //    private final InfoFactory infoFactory;

    //private final MenuFactory menuFactory;

    @Inject
    public MenuPresenter(ClientFacade facade, @Named(RaplaComponent.RaplaResourcesId) I18nBundle i18n, RaplaLocale raplaLocale, Logger logger,
            CalendarSelectionModel model, ReservationController reservationController, RaplaClipboard clipboard/*,  InfoFactory infoFactory,
            MenuFactory menuFactory*/, @SuppressWarnings("rawtypes") MenuView view)
    {
        super(facade, i18n, raplaLocale, logger);
        this.model = model;
        this.reservationController = reservationController;
        this.view = view;
        this.clipboard = clipboard;
        //        this.infoFactory = infoFactory;
      //  this.menuFactory = menuFactory;
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
//                public void insertAfterId(Component component, String id)
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
            if (canCreateReservation())
            {
                if (canUserAllocateSomething(getUser()))
                {
                    ReservationEdit[] editWindows = reservationController.getEditWindows();
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
                                    Date start = getStartDate(model);
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
                    final boolean enabled = reservationController.isAppointmentOnClipboard() && canCreateReservation();
                    final MenuEntry entry = new MenuEntry(text, icon, enabled);
                    menu.add(entry);
                    mapping.put(entry, new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            CalendarSelectionModel model = getService(CalendarSelectionModel.class);
                            Date start = getStartDate(model);
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
                final boolean enabled = reservationController.isAppointmentOnClipboard() && canCreateReservation();
                final MenuEntry entry = new MenuEntry(text, icon, enabled);
                menu.add(entry);
                mapping.put(entry, new Runnable()
                {
                    @Override
                    public void run()
                    {
                        CalendarSelectionModel model = getService(CalendarSelectionModel.class);
                        Date start = getStartDate(model);
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
            if (!canModify(b.getReservation()))
                return;
            final AppointmentBlock appointmentBlock = b.getAppointmentBlock();
            reservationController.edit(appointmentBlock);
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
        {
            final String text = getString("copy");
            final String icon = "icon.copy";
            final boolean enabled = canCreateReservation();
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
            final boolean enabled = canCreateReservation();
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
            boolean canExchangeAllocatables = getQuery().canExchangeAllocatables(appointment.getReservation());
            boolean canModify = canModify(appointment.getReservation());
            String text = !canModify && canExchangeAllocatables ? getString("exchange_allocatables") : getString("edit");
            final boolean enabled = canModify || canExchangeAllocatables;
            final MenuEntry entry = new MenuEntry(text, icon, enabled);
            menu.add(entry);
            mapping.put(entry, new Runnable()
            {
                public void run()
                {
                    try
                    {
                        reservationController.edit(appointmentBlock);
                    }
                    catch (RaplaException e)
                    {
                        view.showException(e);
                    }
                }
            });
        }
        if (!isException)
        {
            final String text = getI18n().format("delete.format", getString("appointment"));
            final String icon = "icon.delete";
            final boolean enabled = canModify(appointment.getReservation());
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
            boolean enabled = true;
            try
            {
                User user = getUser();
                boolean canRead = canRead(appointment, user, getEntityResolver());
                enabled = canRead;
            }
            catch (RaplaException ex)
            {
                getLogger().error("Can't get user", ex);
            }
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
    protected boolean canAllocate()
    {
        //Date start, Date end,
        Collection<Allocatable> allocatables = model.getMarkedAllocatables();
        boolean canAllocate = true;
        Date start = getStartDate(model);
        Date end = getEndDate(model, start);
        for (Allocatable allo : allocatables)
        {
            if (!canAllocate(start, end, allo))
            {
                canAllocate = false;
            }
        }
        return canAllocate;
    }

}