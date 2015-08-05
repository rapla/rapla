package org.rapla.client.plugin.weekview.gwt;

import java.util.Date;
import java.util.List;

import javax.inject.Inject;

import org.rapla.client.base.AbstractView;
import org.rapla.client.gui.menu.gwt.context.ContextCreator;
import org.rapla.client.gwt.view.NavigatorView;
import org.rapla.client.gwt.view.NavigatorView.NavigatorAction;
import org.rapla.client.gwt.view.WeekviewGWT;
import org.rapla.client.gwt.view.WeekviewGWT.Callback;
import org.rapla.client.plugin.weekview.CalendarWeekView;
import org.rapla.client.plugin.weekview.CalendarWeekViewPresenter.HTMLWeekViewPresenter.HTMLDaySlot;
import org.rapla.client.plugin.weekview.CalendarWeekViewPresenter.HTMLWeekViewPresenter.RowSlot;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.gui.PopupContext;
import org.rapla.plugin.abstractcalendar.server.HTMLRaplaBlock;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.IsWidget;

public class CalendarWeekViewImpl extends AbstractView<org.rapla.client.plugin.weekview.CalendarWeekView.Presenter> implements CalendarWeekView<IsWidget>,
        NavigatorAction, Callback
{

    private final WeekviewGWT calendar;
    private final NavigatorView navigatorView;
    Logger logger;

    @Inject
    public CalendarWeekViewImpl(Logger logger, RaplaLocale locale, ContextCreator contextCreator)
    {
        navigatorView = new NavigatorView("week", this, locale);
        calendar = new WeekviewGWT("week", logger, this, contextCreator);
        this.logger = logger;
    }

    @Override
    public IsWidget provideContent()
    {
        FlowPanel container = new FlowPanel();
        container.add(navigatorView);
        container.add(calendar);
        return container;
    }

    @Override
    public void update(final List<HTMLDaySlot> daylist, final List<RowSlot> timelist, final String weeknumber, final Date selectedDate)
    {
        navigatorView.setDate(selectedDate);
        calendar.update(daylist, timelist, weeknumber);
    }

    @Override
    public void selectedDate(Date selectedDate)
    {
        getPresenter().selectDate(selectedDate);
    }

    @Override
    public void next()
    {
        getPresenter().next();
    }

    @Override
    public void previous()
    {
        getPresenter().previous();
    }

    @Override
    public void updateReservation(HTMLRaplaBlock block, HTMLDaySlot daySlot, Integer rowSlot, PopupContext context)
    {
        try
        {
            getPresenter().updateReservation(block, daySlot, rowSlot, context);
        }
        catch (RaplaException e)
        {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void selectReservation(HTMLRaplaBlock block, PopupContext context)
    {
        getPresenter().selectReservation(block, context);
    }

    @Override
    public void newReservation(HTMLDaySlot daySlot, Integer fromMinuteOfDay, Integer tillMinuteOfDay, final PopupContext context)
    {
        try
        {
            getPresenter().newReservation(daySlot, fromMinuteOfDay, tillMinuteOfDay, context);
        }
        catch (RaplaException e)
        {
            logger.error(e.getMessage(), e);
        }
    }
}
