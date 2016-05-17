/*--------------------------------------------------------------------------*
 | Copyright (C) 2006  Christopher Kohlhaas                                 |
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

package org.rapla.plugin.timeslot.client.swing;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.JLabel;

import org.rapla.RaplaResources;
import org.rapla.client.EditController;
import org.rapla.client.PopupContext;
import org.rapla.client.ReservationController;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.extensionpoints.ObjectMenuFactory;
import org.rapla.client.internal.RaplaClipboard;
import org.rapla.client.swing.InfoFactory;
import org.rapla.client.swing.MenuFactory;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.components.calendar.DateRenderer;
import org.rapla.components.calendarview.Block;
import org.rapla.components.calendarview.CalendarView;
import org.rapla.components.calendarview.GroupStartTimesStrategy;
import org.rapla.components.calendarview.swing.AbstractSwingCalendar;
import org.rapla.components.calendarview.swing.SwingBlock;
import org.rapla.components.calendarview.swing.SwingCompactWeekView;
import org.rapla.components.calendarview.swing.ViewListener;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarOptions;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.plugin.abstractcalendar.AbstractRaplaBlock;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;
import org.rapla.plugin.abstractcalendar.RaplaCalendarViewListener;
import org.rapla.plugin.abstractcalendar.client.swing.AbstractRaplaSwingCalendar;
import org.rapla.plugin.timeslot.Timeslot;
import org.rapla.plugin.timeslot.TimeslotProvider;
import org.rapla.scheduler.Promise;

public class SwingCompactDayCalendar extends AbstractRaplaSwingCalendar
{
    List<Timeslot> timeslots;
    private final TimeslotProvider timeslotProvider;

    public SwingCompactDayCalendar(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, CalendarModel settings, boolean editable,
            boolean printing, Set<ObjectMenuFactory> objectMenuFactories, MenuFactory menuFactory, CalendarSelectionModel calendarSelectionModel,
            RaplaClipboard clipboard, TimeslotProvider timeslotProvider, ReservationController reservationController, InfoFactory infoFactory,
            RaplaImages raplaImages, DateRenderer dateRenderer, DialogUiFactoryInterface dialogUiFactory, IOInterface ioInterface,
            AppointmentFormater appointmentFormater, EditController editController) throws RaplaException
    {
        super(facade, i18n, raplaLocale, logger, settings, editable, printing, objectMenuFactories, menuFactory, null, calendarSelectionModel, clipboard,
                reservationController, infoFactory, raplaImages, dateRenderer, dialogUiFactory, ioInterface, appointmentFormater, editController);
        this.timeslotProvider = timeslotProvider;
    }

    protected AbstractSwingCalendar createView(boolean showScrollPane) throws RaplaException
    {
        SwingCompactWeekView compactWeekView = new SwingCompactWeekView(showScrollPane)
        {
            @Override protected JComponent createColumnHeader(Integer column)
            {
                JLabel component = (JLabel) super.createColumnHeader(column);
                if (column != null)
                {
                    try
                    {
                        List<Allocatable> sortedAllocatables = getSortedAllocatables();
                        Allocatable allocatable = sortedAllocatables.get(column);
                        String name = allocatable.getName(getLocale());
                        component.setText(name);
                    }
                    catch (RaplaException e)
                    {
                    }
                }
                else
                {
                    String dateText = getRaplaLocale().formatDateShort(getStartDate());
                    component.setText(dateText);
                }
                return component;
            }

            protected int getColumnCount()
            {
                try
                {
                    Collection<Allocatable> selectedAllocatables = model.getSelectedAllocatablesAsList();
                    return selectedAllocatables.size();
                }
                catch (RaplaException e)
                {
                    return 0;
                }
            }

            @Override public TimeInterval normalizeBlockIntervall(SwingBlock block)
            {
                Date start = block.getStart();
                Date end = block.getEnd();
                for (Timeslot slot : timeslots)
                {
                    int minuteOfDay = DateTools.getMinuteOfDay(start.getTime());
                    int minuteOfDay1 = slot.getMinuteOfDay();
                    if (minuteOfDay >= minuteOfDay1)
                    {
                        start = new Date(DateTools.cutDate(start).getTime() + minuteOfDay1);
                        break;
                    }
                }
                for (Timeslot slot : timeslots)
                {
                    int minuteOfDay = DateTools.getMinuteOfDay(end.getTime());
                    final int minuteOfDay1 = slot.getMinuteOfDay();
                    if (minuteOfDay < minuteOfDay1)
                    {
                        end = new Date(DateTools.cutDate(end).getTime() + minuteOfDay1);
                    }
                    if (minuteOfDay1 > minuteOfDay)
                    {
                        break;
                    }

                }
                return new TimeInterval(start, end);
            }

        };
        compactWeekView.setDaysInView(1);
        return compactWeekView;

    }

    protected ViewListener createListener() throws RaplaException
    {
        return new RaplaCalendarViewListener(getClientFacade(), getI18n(), getRaplaLocale(), getLogger(), model, view.getComponent(), objectMenuFactories,
                menuFactory, calendarSelectionModel, clipboard, reservationController, infoFactory, raplaImages, dialogUiFactory, editController)
        {
            @Override protected Collection<Allocatable> getMarkedAllocatables()
            {
                List<Allocatable> selectedAllocatables = getSortedAllocatables();
                int columns = selectedAllocatables.size();
                Set<Allocatable> allSelected = new HashSet<Allocatable>();
                int slots = columns * timeslots.size();
                for (int i = 0; i < slots; i++)
                {
                    if (view.isSelected(i))
                    {
                        int column = i % columns;
                        Allocatable allocatable = selectedAllocatables.get(column);
                        allSelected.add(allocatable);
                    }
                }
                if (selectedAllocatables.size() == 1)
                {
                    allSelected.add(selectedAllocatables.get(0));
                }
                return allSelected;

            }

            @Override public void selectionChanged(Date start, Date end)
            {
                TimeInterval inter = getMarkedInterval(start);
                super.selectionChanged(inter.getStart(), inter.getEnd());
            }

            protected TimeInterval getMarkedInterval(Date start)
            {
                List<Allocatable> selectedAllocatables = getSortedAllocatables();
                int columns = selectedAllocatables.size();
                Date end;
                Integer startTime = null;
                Integer endTime = null;
                int slots = columns * timeslots.size();

                for (int i = 0; i < slots; i++)
                {
                    if (view.isSelected(i))
                    {
                        int index = i / columns;
                        int time = timeslots.get(index).getMinuteOfDay();
                        if (startTime == null || time < startTime)
                        {
                            startTime = time;
                        }

                        time = index < timeslots.size() - 1 ? timeslots.get(index + 1).getMinuteOfDay() : 24 * 60;
                        if (endTime == null || time >= endTime)
                        {
                            endTime = time;
                        }
                    }
                }

                CalendarOptions calendarOptions = getCalendarOptions();
                if (startTime == null)
                {
                    startTime = calendarOptions.getWorktimeStartMinutes();
                }
                if (endTime == null)
                {
                    endTime = calendarOptions.getWorktimeEndMinutes() + (calendarOptions.isWorktimeOvernight() ? 24 * 60 : 0);
                }

                start = DateTools.toDateTime(start, new Date(DateTools.toTime(startTime / 60, startTime % 60, 0)));
                end = DateTools.toDateTime(start, new Date(DateTools.toTime(endTime / 60, endTime % 60, 0)));
                TimeInterval intervall = new TimeInterval(start, end);
                return intervall;
            }

            @Override public void moved(Block block, Point p, Date newStart, int slotNr)
            {
                int index = slotNr;//getIndex( selectedAllocatables, block );

                if (index < 0)
                {
                    return;
                }

                try
                {
                    final List<Allocatable> selectedAllocatables = getSortedAllocatables();
                    int columns = selectedAllocatables.size();
                    int column = index % columns;
                    Allocatable newAlloc = selectedAllocatables.get(column);
                    AbstractRaplaBlock raplaBlock = (AbstractRaplaBlock) block;
                    Allocatable oldAlloc = raplaBlock.getGroupAllocatable();
                    int rowIndex = index / columns;
                    Timeslot timeslot = timeslots.get(rowIndex);
                    int time = timeslot.getMinuteOfDay();
                    int lastMinuteOfDay;
                    final DateTools.TimeWithoutTimezone timeWithoutTimezone = DateTools.toTime(block.getStart().getTime());
                    lastMinuteOfDay = timeWithoutTimezone.hour * 60 + timeWithoutTimezone.minute;
                    boolean sameTimeSlot = true;
                    if (lastMinuteOfDay < time)
                    {
                        sameTimeSlot = false;
                    }
                    if (rowIndex + 1 < timeslots.size())
                    {
                        Timeslot nextTimeslot = timeslots.get(rowIndex + 1);
                        if (lastMinuteOfDay >= nextTimeslot.getMinuteOfDay())
                        {
                            sameTimeSlot = false;
                        }
                    }

                    if (sameTimeSlot)
                    {
                        time = lastMinuteOfDay;
                    }
                    final long l = DateTools.toTime(time / 60, time % 60, 0);
                    newStart = DateTools.toDateTime(newStart, new Date(l));
                    if (newAlloc != null && oldAlloc != null && !newAlloc.equals(oldAlloc))
                    {
                        AppointmentBlock appointmentBlock = raplaBlock.getAppointmentBlock();
                        PopupContext popupContext = createPopupContext(getMainComponent(), p);
                        reservationController.exchangeAllocatable(appointmentBlock, oldAlloc, newAlloc, newStart, popupContext);
                    }
                    else
                    {

                        moved(block, p, newStart);
                    }
                }
                catch (RaplaException ex)
                {
                    dialogUiFactory.showException(ex, new SwingPopupContext(getMainComponent(), null));
                }

            }

        };
    }

    protected Promise<RaplaBuilder> createBuilder() 
    {
        Promise<RaplaBuilder> builderPromise = super.createBuilder();
        final Promise<RaplaBuilder> nextBuilderPromise = builderPromise.thenApply((builder) ->
        {
            timeslots = timeslotProvider.getTimeslots();
            List<Integer> startTimes = new ArrayList<Integer>();
            for (Timeslot slot : timeslots)
            {
                startTimes.add(slot.getMinuteOfDay());
            }

            final List<Allocatable> allocatables = getSortedAllocatables();
            builder.setSmallBlocks(true);
            builder.setSplitByAllocatables(true);
            GroupStartTimesStrategy strategy = new GroupStartTimesStrategy()
            {
                @Override
                protected Map<Block, Integer> getBlockMap(CalendarView wv, List<Block> blocks)
                {
                    if (allocatables != null)
                    {
                        Map<Block, Integer> map = new LinkedHashMap<Block, Integer>();
                        for (Block block : blocks)
                        {
                            int index = getIndex(allocatables, block);

                            if (index >= 0)
                            {
                                map.put(block, index);
                            }
                        }
                        return map;
                    }
                    else
                    {
                        return super.getBlockMap(wv, blocks);
                    }
                }
            };
            strategy.setAllocatables(allocatables);
            strategy.setFixedSlotsEnabled(true);
            strategy.setResolveConflictsEnabled(false);
            strategy.setStartTimes(startTimes);
            builder.setBuildStrategy(strategy);

            String[] slotNames = new String[timeslots.size()];
            int maxSlotLength = 5;
            for (int i = 0; i < timeslots.size(); i++)
            {
                String slotName = timeslots.get(i).getName();
                maxSlotLength = Math.max(maxSlotLength, slotName.length());
                slotNames[i] = slotName;
            }
            ((SwingCompactWeekView) view).setLeftColumnSize(30 + maxSlotLength * 6);
            // builder.setSplitByAllocatables( false );

            ((SwingCompactWeekView) view).setSlots(slotNames);
            return builder;
        });
        return nextBuilderPromise;
    }

    private int getIndex(final List<Allocatable> allocatables, Block block)
    {
        AbstractRaplaBlock b = (AbstractRaplaBlock) block;
        Allocatable a = b.getGroupAllocatable();
        int index = a != null ? allocatables.indexOf(a) : -1;
        return index;
    }

    protected void configureView() throws RaplaException
    {
        view.setToDate(model.getSelectedDate());
        //        if ( !view.isEditable() ) {
        //            view.setSlotSize( model.getSize());
        //        } else {
        //            view.setSlotSize( 200 );
        //        }
    }

    public DateTools.IncrementSize getIncrementSize()
    {
        return DateTools.IncrementSize.DAY_OF_YEAR;
    }

}
