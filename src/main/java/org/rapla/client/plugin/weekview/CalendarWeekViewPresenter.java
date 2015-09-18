package org.rapla.client.plugin.weekview;

import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;

import org.rapla.RaplaResources;
import org.rapla.client.base.CalendarPlugin;
import org.rapla.client.edit.reservation.sample.ReservationPresenter;
import org.rapla.client.event.StartActivityEvent;
import org.rapla.client.gui.menu.MenuPresenter;
import org.rapla.client.plugin.weekview.CalendarWeekView.Presenter;
import org.rapla.components.util.DateTools;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.CalendarOptions;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.gui.PopupContext;
import org.rapla.gui.ReservationController;
import org.rapla.inject.Extension;
import org.rapla.plugin.abstractcalendar.GroupAllocatablesStrategy;
import org.rapla.plugin.abstractcalendar.HTMLRaplaBlock;
import org.rapla.plugin.abstractcalendar.HTMLRaplaBuilder;

import com.google.web.bindery.event.shared.EventBus;

@Extension(provides = CalendarPlugin.class, id = CalendarWeekViewPresenter.WEEK_VIEW)
public class CalendarWeekViewPresenter<W> implements Presenter, CalendarPlugin<W>
{
    public static final String WEEK_VIEW = "week";

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
    private HTMLRaplaBuilder builder;

    @Inject
    private RaplaLocale raplaLocale;

    @Inject
    private RaplaResources i18n;

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
    public boolean isEnabled()
    {
        //Boolean result =  facade.getSystemPreferences().getEntryAsBoolean(ENABLED, DEFAULT_ENABLED);
        return true;
    }

    @Override
    public Date calcNext(Date currentDate)
    {
        return DateTools.addDays(currentDate, 7);
    }

    @Override
    public Date calcPrevious(Date currentDate)
    {
        return DateTools.subDays(currentDate, 7);
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
        final Reservation reservation = appointment.getReservation();
        eventBus.fireEvent(new StartActivityEvent(ReservationPresenter.EDIT_ACTIVITY_ID, reservation.getId()));
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
    public void resizeReservation(HTMLRaplaBlock block, HTMLDaySlot daySlot, Integer minuteOfDay, PopupContext context) throws RaplaException
    {

        AppointmentBlock appointmentBlock = block.getAppointmentBlock();
        final Date appintmentStart = appointmentBlock.getAppointment().getStart();
        final Date date1 = calcDate(daySlot, minuteOfDay);
        final Date date2 = DateTools.toDateTime(date1, appintmentStart);
        final Date newStart;
        final Date newEnd;
        if (date1.getTime() < date2.getTime())
        {
            newStart = date1;
            newEnd = date2;
        }
        else
        {
            newStart = date2;
            newEnd = date1;
        }
        boolean keepTime = false;
        reservationController.resizeAppointment(appointmentBlock, newStart, newEnd, context, keepTime);

    }

    @Override
    public void newReservation(final HTMLDaySlot daySlot, final Integer fromMinuteOfDay, final Integer tillMinuteOfDay, PopupContext context)
            throws RaplaException
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
        HTMLWeekViewPresenter weekView = new HTMLWeekViewPresenter(view, logger);
        configure(weekView);
        Date startDate = weekView.getStartDate();
        Date endDate = weekView.getEndDate();
        model.setStartDate(startDate);
        model.setEndDate(endDate);
        String weeknumber = i18n.calendarweek(startDate);
        weekView.setWeeknumber(weeknumber);
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
}
