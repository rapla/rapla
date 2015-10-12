package org.rapla.plugin.weekview.client.weekview;

import org.rapla.client.base.View;
import org.rapla.plugin.weekview.client.weekview.CalendarWeekView.Presenter;
import org.rapla.plugin.weekview.client.weekview.HTMLWeekViewPresenter.RowSlot;
import org.rapla.framework.RaplaException;
import org.rapla.client.PopupContext;
import org.rapla.plugin.abstractcalendar.HTMLRaplaBlock;

import java.util.List;

public interface CalendarWeekView<W> extends View<Presenter>
{
    public interface Presenter
    {
        void updateReservation(HTMLRaplaBlock block, HTMLDaySlot daySlot, Integer minuteOfDay, PopupContext context) throws RaplaException;

        void selectReservation(HTMLRaplaBlock block, PopupContext context);
        
        void newReservation(HTMLDaySlot daySlot, Integer fromMinuteOfDay, Integer tillMinuteOfDay, PopupContext context) throws RaplaException;

        void resizeReservation(HTMLRaplaBlock block, HTMLDaySlot daySlot, Integer minuteOfDay, PopupContext context) throws RaplaException;
    }

    W provideContent();

    void update(List<HTMLDaySlot> daylist, List<RowSlot> timelist, String weeknumber);

}
