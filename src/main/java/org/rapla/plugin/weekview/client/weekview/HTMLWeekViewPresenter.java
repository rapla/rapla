package org.rapla.plugin.weekview.client.weekview;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.rapla.components.calendarview.Block;
import org.rapla.components.calendarview.Builder;
import org.rapla.components.calendarview.Builder.PreperationResult;
import org.rapla.components.calendarview.html.AbstractHTMLView;
import org.rapla.components.util.DateTools;
import org.rapla.logger.Logger;
import org.rapla.plugin.abstractcalendar.HTMLRaplaBlock;

public class HTMLWeekViewPresenter extends AbstractHTMLView
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

    public HTMLWeekViewPresenter(CalendarWeekView<?> view, Logger logger)
    {
        this.view = view;
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
        List<HTMLWeekViewPresenter.RowSlot> timelist = new ArrayList<>();
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
                        HTMLWeekViewPresenter.Slot slot = daySlots[day].getSlotAt(slotnr);
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
                    List<HTMLWeekViewPresenter.SpanAndMinute> rowTimes = new ArrayList<HTMLWeekViewPresenter.SpanAndMinute>();
                    for (int i = 0; i < m_rowsPerHour; i++)
                    {
                        final int startMinute = minuteOfDay + (60 / m_rowsPerHour) * i;
                        final int endMinute = startMinute + (60 / m_rowsPerHour);
                        int rowspanForTimeUnit = calcRowspan(startMinute, endMinute);
                        if (rowspanForTimeUnit == 0)
                        {
                            rowspanForTimeUnit++;
                        }
                        HTMLWeekViewPresenter.SpanAndMinute spanAndMinute = new SpanAndMinute();
                        spanAndMinute.minute = startMinute;
                        spanAndMinute.rowspan = rowspanForTimeUnit;
                        rowTimes.add(spanAndMinute);
                    }
                    final HTMLWeekViewPresenter.RowSlot rowSlot = new RowSlot(timeString, rowspan, rowTimes);
                    timelist.add(rowSlot);
                }
            }
            logger.info("tableprep took  " + (System.currentTimeMillis() - time) + " ms");
        }
        {
            long time = System.currentTimeMillis();
            view.update(daylist, timelist, weeknumber);
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

        public RowSlot(final String rowname, final int rowspan, final List<HTMLWeekViewPresenter.SpanAndMinute> rowTimes)
        {
            this.rowname = rowname;
            this.rowspan = rowspan;
            this.rowTimes = Collections.unmodifiableList(rowTimes);
        }

        private final String rowname;
        private final int rowspan;
        private final List<HTMLWeekViewPresenter.SpanAndMinute> rowTimes;

        public String getRowname()
        {
            return rowname;
        }

        public int getRowspan()
        {
            return rowspan;
        }

        public List<HTMLWeekViewPresenter.SpanAndMinute> getRowTimes()
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