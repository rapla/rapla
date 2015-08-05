package org.rapla.client.plugin.weekview;

import java.util.Date;
import java.util.List;

import org.rapla.client.base.View;
import org.rapla.client.gui.menu.data.MenuCallback;
import org.rapla.client.gui.menu.data.MenuEntry;
import org.rapla.client.gui.menu.data.Point;
import org.rapla.client.plugin.weekview.CalendarWeekView.Presenter;
import org.rapla.client.plugin.weekview.CalendarWeekViewPresenter.HTMLWeekViewPresenter.HTMLDaySlot;
import org.rapla.client.plugin.weekview.CalendarWeekViewPresenter.HTMLWeekViewPresenter.RowSlot;
import org.rapla.framework.RaplaException;
import org.rapla.gui.PopupContext;
import org.rapla.plugin.abstractcalendar.server.HTMLRaplaBlock;

public interface CalendarWeekView<W> extends View<Presenter>
{

    public interface Presenter
    {
        
        void updateReservation(HTMLRaplaBlock block, HTMLDaySlot daySlot, Integer minuteOfDay, PopupContext context) throws RaplaException;

        void selectReservation(HTMLRaplaBlock block, PopupContext context);
        
        void selectDate(Date newDate);

        void next();

        void previous();

        void newReservation(HTMLDaySlot daySlot, Integer fromMinuteOfDay, Integer tillMinuteOfDay, PopupContext context) throws RaplaException;
    }

    W provideContent();

    void update(List<HTMLDaySlot> daylist, List<RowSlot> timelist, String weeknumber, Date selectedDate);

}
