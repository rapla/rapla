package org.rapla.plugin.weekview.client.weekview;

import java.util.ArrayList;
import java.util.Date;

import org.rapla.components.calendarview.Block;
import org.rapla.plugin.weekview.client.weekview.HTMLWeekViewPresenter.Slot;

public class HTMLDaySlot extends ArrayList<HTMLWeekViewPresenter.Slot>
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
        HTMLWeekViewPresenter.Slot slot = new Slot();
        add(slot);
        return size();
    }

    public HTMLWeekViewPresenter.Slot getSlotAt(int index)
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