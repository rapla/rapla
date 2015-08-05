package org.rapla.client.plugin.weekview;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.rapla.client.base.CalendarPlugin;
import org.rapla.client.event.DetailSelectEvent;
import org.rapla.client.gui.menu.MenuPresenter;
import org.rapla.client.plugin.weekview.CalendarWeekView.Presenter;
import org.rapla.client.plugin.weekview.CalendarWeekViewPresenter.HTMLWeekViewPresenter.HTMLDaySlot;
import org.rapla.components.calendarview.Block;
import org.rapla.components.calendarview.Builder;
import org.rapla.components.calendarview.Builder.PreperationResult;
import org.rapla.components.calendarview.html.AbstractHTMLView;
import org.rapla.components.util.DateTools;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.facade.CalendarOptions;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.gui.PopupContext;
import org.rapla.gui.ReservationController;
import org.rapla.plugin.abstractcalendar.GroupAllocatablesStrategy;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;
import org.rapla.plugin.abstractcalendar.server.HTMLRaplaBlock;
import org.rapla.plugin.abstractcalendar.server.HTMLRaplaBuilder;

import com.google.web.bindery.event.shared.EventBus;

@Singleton
public class CalendarWeekViewPresenter<W> implements Presenter, CalendarPlugin<W>
{

    private CalendarWeekView<W> view;
    @Inject
    ReservationController reservationController;

    @Inject
    private Logger logger;
    @Inject
    private EventBus eventBus;
    @Inject
    private CalendarSelectionModel model;

    @Inject
    private ClientFacade facade;

    @Inject
    private HTMLRaplaBuilder builderProvider;

    @Inject
    private RaplaLocale raplaLocale;

    @Inject
    private @Named(RaplaComponent.RaplaResourcesId) I18nBundle i18n;
    
    @Inject
    private MenuPresenter presenter;

//    @Inject
//    private MenuFactory menuFactory;
//    @Inject
//    private MenuView menuView;

    
    @SuppressWarnings("unchecked")
    @Inject
    public CalendarWeekViewPresenter(CalendarWeekView view)
    {
        this.view = (CalendarWeekView<W>) view;
        this.view.setPresenter(this);
    }

    @Override
    public String getName()
    {
        return "week";
    }

    @Override
    public W provideContent()
    {
        return view.provideContent();
    }

    @Override
    public void selectReservation(HTMLRaplaBlock block, PopupContext context)
    {
        final AppointmentBlock appointmentBlock = block.getAppointmentBlock();
        final Appointment appointment = appointmentBlock.getAppointment();
        eventBus.fireEvent(new DetailSelectEvent(appointment.getReservation(), context));
    }

    @Override
    public void updateReservation(HTMLRaplaBlock block, HTMLDaySlot daySlot, Integer minuteOfDay, PopupContext context) throws RaplaException
    {
        AppointmentBlock appointmentBlock = block.getAppointmentBlock();
        Date newStart = calcDate(daySlot, minuteOfDay);
        boolean keepTime = false;
        reservationController.moveAppointment(appointmentBlock, newStart, context, keepTime);
    }
    
    @Override
    public void newReservation(final HTMLDaySlot daySlot, final Integer fromMinuteOfDay, final Integer tillMinuteOfDay, PopupContext context) throws RaplaException
    {
        presenter.selectionPopup(context);
    }

    private Date calcDate(HTMLDaySlot daySlot, Integer minuteOfDay)
    {
        Date newStartTime = new Date(minuteOfDay * DateTools.MILLISECONDS_PER_MINUTE);
        Date newStartDate = daySlot.getStartDate();
        Date newDate = DateTools.toDateTime(newStartDate, newStartTime);
        return newDate;
    }

    @Override
    public void updateContent() throws RaplaException
    {
        final Date selectedDate = model.getSelectedDate();
        HTMLWeekViewPresenter weekView = new HTMLWeekViewPresenter(view, logger, selectedDate);
        configure(weekView);
        Date startDate = weekView.getStartDate();
        Date endDate = weekView.getEndDate();
        model.setStartDate(startDate);
        model.setEndDate(endDate);
        String weeknumber = i18n.format("calendarweek.abbreviation", startDate);
        weekView.setWeeknumber(weeknumber);
        RaplaBuilder builder = builderProvider;
        builder.setNonFilteredEventsVisible(false);
        {
            long time = System.currentTimeMillis();
            builder.setFromModel(model, startDate, endDate);
            logger.info("events loaded took  " + (System.currentTimeMillis() - time) + " ms");
        }

        GroupAllocatablesStrategy strategy = new GroupAllocatablesStrategy(raplaLocale.getLocale());
        boolean compactColumns = getCalendarOptions().isCompactColumns() || builder.getAllocatables().size() == 0;
        //compactColumns = false;
        strategy.setFixedSlotsEnabled(!compactColumns);
        strategy.setResolveConflictsEnabled(true);
        builder.setBuildStrategy(strategy);
        weekView.rebuild(builder);
        //String calendarviewHTML = weekview.getHtml();
        //this.view.update(calendarviewHTML);
    }

    private void configure(HTMLWeekViewPresenter weekView) throws RaplaException
    {
        CalendarOptions opt = getCalendarOptions();
        weekView.setRowsPerHour(opt.getRowsPerHour());
        weekView.setWorktimeMinutes(opt.getWorktimeStartMinutes(), opt.getWorktimeEndMinutes());
        weekView.setFirstWeekday(opt.getFirstDayOfWeek());
        int days = getDays(opt);
        weekView.setDaysInView(days);
        Set<Integer> excludeDays = opt.getExcludeDays();
        if (days < 3)
        {
            excludeDays = new HashSet<Integer>();
        }
        weekView.setExcludeDays(excludeDays);
        weekView.setLocale(raplaLocale);
        weekView.setToDate(model.getSelectedDate());
    }

    private CalendarOptions getCalendarOptions() throws RaplaException
    {
        return RaplaComponent.getCalendarOptions(facade.getUser(), facade);
    }

    //    protected HTMLWeekView createCalendarView() {
    //        HTMLWeekView weekView = new HTMLWeekView()
    //        {
    //            public void rebuild() {
    //                super.rebuild();
    //            }
    //        };
    //        return weekView;
    //    }

    /** overide this for daily views*/
    protected int getDays(CalendarOptions calendarOptions)
    {
        return calendarOptions.getDaysInWeekview();
    }

    public int getIncrementSize()
    {
        return Calendar.WEEK_OF_YEAR;
    }

    static public class HTMLWeekViewPresenter extends AbstractHTMLView
    {
        private CalendarWeekView<?> view;

        private int endMinutes;
        private int minMinute;
        private int maxMinute;
        private int startMinutes;
        int m_rowsPerHour = 2;
        HTMLDaySlot[] daySlots;
        ArrayList<Block> blocks = new ArrayList<Block>();
        String weeknumber;
        Logger logger;

        private final Date selectedDate;

        public HTMLWeekViewPresenter(CalendarWeekView<?> view, Logger logger, Date selectedDate)
        {
            this.view = view;
            this.selectedDate = selectedDate;
            this.logger = logger;
        }

        /** The granularity of the selection rows.
         * <ul>
         * <li>1:  1 rows per hour =   1 Hour</li>
         * <li>2:  2 rows per hour = 1/2 Hour</li>
         * <li>3:  3 rows per hour = 20 Minutes</li>
         * <li>4:  4 rows per hour = 15 Minutes</li>
         * <li>6:  6 rows per hour = 10 Minutes</li>
         * <li>12: 12 rows per hour =  5 Minutes</li>
         * </ul>
         * Default is 2.
         */
        public void setRowsPerHour(int rows)
        {
            m_rowsPerHour = rows;
        }

        public void setWeeknumber(String weeknumber)
        {
            this.weeknumber = weeknumber;
        }

        public int getRowsPerHour()
        {
            return m_rowsPerHour;
        }

        public void setWorktime(int startHour, int endHour)
        {
            this.startMinutes = startHour * 60;
            this.endMinutes = endHour * 60;
        }

        public void setWorktimeMinutes(int startMinutes, int endMinutes)
        {
            this.startMinutes = startMinutes;
            this.endMinutes = endMinutes;
        }

        public void setToDate(Date weekDate)
        {
            calcMinMaxDates(weekDate);
        }

        public Collection<Block> getBlocks()
        {
            return blocks;
        }

        /** must be called after the slots are filled*/
        protected boolean isEmpty(int column)
        {
            return daySlots[column].isEmpty();
        }

        protected int getColumnCount()
        {
            return getDaysInView();
        }

        public void rebuild(Builder b)
        {
            int columns = getColumnCount();
            {
                long time = System.currentTimeMillis();
                PreperationResult prepareBuild;
                {
                    blocks.clear();
                    daySlots = new HTMLDaySlot[columns];

                    String[] headerNames = new String[columns];

                    for (int i = 0; i < columns; i++)
                    {
                        String headerName = createColumnHeader(i);
                        headerNames[i] = headerName;
                    }

                    // calculate the blocks
                    int start = startMinutes;
                    int end = endMinutes;
                    minuteBlock.clear();
                    Date startDate = getStartDate();
                    prepareBuild = b.prepareBuild(startDate, getEndDate());
                    start = Math.min(prepareBuild.getMinMinutes(), start);
                    end = Math.max(prepareBuild.getMaxMinutes(), end);
                    if (start < 0)
                        throw new IllegalStateException("builder.getMin() is smaller than 0");
                    if (end > 24 * 60)
                        throw new IllegalStateException("builder.getMax() is greater than 24");

                    minMinute = start;
                    maxMinute = end;
                    for (int i = 0; i < daySlots.length; i++)
                    {
                        Date date = DateTools.addDays(startDate, i);
                        daySlots[i] = new HTMLDaySlot(2, headerNames[i], date);
                    }
                }
                {
                    b.build(this, prepareBuild.getBlocks());
                }
                logger.info("building took  " + (System.currentTimeMillis() - time) + " ms");
            }

            for (int minuteOfDay = minMinute; minuteOfDay < maxMinute; minuteOfDay++)
            {
                boolean isLine = (minuteOfDay) % (60 / m_rowsPerHour) == 0;
                if (isLine || minuteOfDay == minMinute)
                {
                    minuteBlock.add(minuteOfDay);
                }
            }

            List<HTMLDaySlot> daylist = new ArrayList<>();
            for (int i = 0; i < daySlots.length; i++)
            {
                if (isExcluded(i))
                    continue;
                daylist.add(daySlots[i]);
            }
            List<RowSlot> timelist = new ArrayList<>();
            {
                long time = System.currentTimeMillis();

                int row = 0;
                for (Integer minuteOfDay : minuteBlock)
                {
                    row++;

                    for (int day = 0; day < columns; day++)
                    {
                        if (isExcluded(day))
                            continue;

                        for (int slotnr = 0; slotnr < daySlots[day].size(); slotnr++)
                        {
                            Slot slot = daySlots[day].getSlotAt(slotnr);
                            Block block = slot.getBlock(minuteOfDay);
                            if (block != null)
                            {
                                int endMinute = Math.min(maxMinute, DateTools.getMinuteOfDay(block.getEnd().getTime()));
                                int rowspan = calcRowspan(minuteOfDay, endMinute);
                                if (block instanceof HTMLRaplaBlock)
                                {
                                    ((HTMLRaplaBlock) block).setRowCount(rowspan);
                                    ((HTMLRaplaBlock) block).setRow(row);
                                }
                                slot.setLastEnd(endMinute);
                            }
                        }
                    }
                    if (minuteBlock.last().equals(minuteOfDay))
                    {
                        break;
                    }
                    boolean fullHour = (minuteOfDay) % 60 == 0;
                    //                    boolean isLine = (minuteOfDay) % (60 / m_rowsPerHour) == 0;
                    if (fullHour || minuteOfDay == minMinute)
                    {
                        String timeString = getRaplaLocale().formatTime(minuteOfDay);
                        int rowspan = calcRowspan(minuteOfDay, ((minuteOfDay / 60) + 1) * 60);
                        List<SpanAndMinute> rowTimes = new ArrayList<SpanAndMinute>();
                        for (int i = 0; i < m_rowsPerHour; i++)
                        {
                            final int startMinute = minuteOfDay + (60 / m_rowsPerHour) * i;
                            final int endMinute = startMinute + (60 / m_rowsPerHour);
                            int rowspanForTimeUnit = calcRowspan(startMinute, endMinute);
                            if (rowspanForTimeUnit == 0)
                            {
                                rowspanForTimeUnit++;
                            }
                            SpanAndMinute spanAndMinute = new SpanAndMinute();
                            spanAndMinute.minute = startMinute;
                            spanAndMinute.rowspan = rowspanForTimeUnit;
                            rowTimes.add(spanAndMinute);
                        }
                        final RowSlot rowSlot = new RowSlot(timeString, rowspan, rowTimes);
                        timelist.add(rowSlot);
                    }
                }
                logger.info("tableprep took  " + (System.currentTimeMillis() - time) + " ms");
            }
            {
                long time = System.currentTimeMillis();
                view.update(daylist, timelist, weeknumber, selectedDate);
                logger.info("update took  " + (System.currentTimeMillis() - time) + " ms");
            }

        }

        public static class SpanAndMinute
        {
            private int rowspan;
            private int minute;

            public int getMinute()
            {
                return minute;
            }

            public int getRowspan()
            {
                return rowspan;
            }
        }

        static public class RowSlot
        {

            public RowSlot(final String rowname, final int rowspan, final List<SpanAndMinute> rowTimes)
            {
                this.rowname = rowname;
                this.rowspan = rowspan;
                this.rowTimes = Collections.unmodifiableList(rowTimes);
            }

            private final String rowname;
            private final int rowspan;
            private final List<SpanAndMinute> rowTimes;

            public String getRowname()
            {
                return rowname;
            }

            public int getRowspan()
            {
                return rowspan;
            }

            public List<SpanAndMinute> getRowTimes()
            {
                return rowTimes;
            }

        }

        private int calcRowspan(int start, int end)
        {
            if (start == end)
            {
                return 1;
            }
            SortedSet<Integer> tailSet = minuteBlock.tailSet(start);
            int col = 0;
            for (Integer minute : tailSet)
            {
                if (minute < end)
                {
                    col++;
                }
                else
                {
                    break;
                }
            }
            return col;
        }

        protected void printBlock(StringBuffer result, int firstEventMarkerId, Block block)
        {
            String string = block.toString();
            result.append(string);
        }

        protected String createColumnHeader(int i)
        {
            Date date = DateTools.addDays(getStartDate(), i);
            String headerName = getRaplaLocale().formatDayOfWeekDateMonth(date);
            return headerName;
        }

        SortedSet<Integer> minuteBlock = new TreeSet<Integer>();

        public void addBlock(Block block, int column, int slot)
        {
            checkBlock(block);
            HTMLDaySlot multiSlot = daySlots[column];
            int startMinute = Math.max(minMinute, DateTools.getMinuteOfDay(block.getStart().getTime()));
            int endMinute = (Math.min(maxMinute, DateTools.getMinuteOfDay(block.getEnd().getTime())));
            blocks.add(block);
            //            startBlock.add( startMinute);
            //       endBlock.add( endMinute);
            minuteBlock.add(startMinute);
            minuteBlock.add(endMinute);
            multiSlot.putBlock(block, slot, startMinute);

        }

        static public class HTMLDaySlot extends ArrayList<Slot>
        {
            private static final long serialVersionUID = 1L;
            private boolean empty = true;
            String header;
            Date startDate;

            public HTMLDaySlot(int size, String header, Date startDate)
            {
                super(size);
                this.header = header;
                this.startDate = startDate;
            }

            public Date getStartDate()
            {
                return startDate;
            }

            public void putBlock(Block block, int slotnr, int startMinute)
            {
                while (slotnr >= size())
                {
                    addSlot();
                }
                getSlotAt(slotnr).putBlock(block, startMinute);
                empty = false;
            }

            public int addSlot()
            {
                Slot slot = new Slot();
                add(slot);
                return size();
            }

            public Slot getSlotAt(int index)
            {
                return get(index);
            }

            public String getHeader()
            {
                return header;
            }

            public boolean isEmpty()
            {
                return empty;
            }
        }

        public static class Slot
        {
            //            int[] EMPTY = new int[]{-2};
            //      int[] SKIP = new int[]{-1};
            int lastEnd = 0;
            HashMap<Integer, Block> map = new HashMap<Integer, Block>();

            public Slot()
            {
            }

            void putBlock(Block block, int startMinute)
            {
                map.put(startMinute, block);
            }

            public Block getBlock(Integer startMinute)
            {
                return map.get(startMinute);
            }

            public Collection<Block> getBlocks()
            {
                return map.values();
            }

            public int getLastEnd()
            {
                return lastEnd;
            }

            void setLastEnd(int lastEnd)
            {
                this.lastEnd = lastEnd;
            }

        }
    }

    @Override
    public void selectDate(Date newDate)
    {
        model.setSelectedDate(newDate);
        updateInternal();
    }

    @Override
    public void next()
    {
        final Date selectedDate = model.getSelectedDate();
        final Date nextDate = DateTools.addDays(selectedDate, 7);
        model.setSelectedDate(nextDate);
        updateInternal();
    }

    @Override
    public void previous()
    {
        final Date selectedDate = model.getSelectedDate();
        final Date nextDate = DateTools.subDays(selectedDate, 7);
        model.setSelectedDate(nextDate);
        updateInternal();
    }

    private void updateInternal()
    {
        try
        {
            long time = System.currentTimeMillis();
            updateContent();
            logger.info("update interval  " + (System.currentTimeMillis() - time) + " ms");
        }
        catch (RaplaException e)
        {
            logger.error(e.getMessage(), e);
        }
    }
}
