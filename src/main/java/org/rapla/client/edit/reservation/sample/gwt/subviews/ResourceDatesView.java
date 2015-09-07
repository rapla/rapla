package org.rapla.client.edit.reservation.sample.gwt.subviews;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import org.gwtbootstrap3.client.ui.Button;
import org.gwtbootstrap3.client.ui.Column;
import org.gwtbootstrap3.client.ui.Container;
import org.gwtbootstrap3.client.ui.Row;
import org.gwtbootstrap3.client.ui.constants.ColumnSize;
import org.gwtbootstrap3.client.ui.constants.IconType;
import org.gwtbootstrap3.client.ui.html.Div;
import org.rapla.RaplaResources;
import org.rapla.client.edit.reservation.sample.ReservationView.Presenter;
import org.rapla.client.edit.reservation.sample.gwt.ReservationViewImpl.ReservationViewPart;
import org.rapla.client.gwt.components.CheckBoxComponent;
import org.rapla.client.gwt.components.CheckBoxComponent.CheckBoxChangeListener;
import org.rapla.client.gwt.components.ClockPicker;
import org.rapla.client.gwt.components.ClockPicker.TimeChangeListener;
import org.rapla.client.gwt.components.DateRangeComponent;
import org.rapla.client.gwt.components.DateRangeComponent.DateRangeChangeListener;
import org.rapla.client.gwt.components.DropDownInputField;
import org.rapla.client.gwt.components.DropDownInputField.DropDownItem;
import org.rapla.client.gwt.components.DropDownInputField.DropDownValueChanged;
import org.rapla.components.i18n.BundleManager;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaLocale;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

public class ResourceDatesView implements ReservationViewPart
{
    private static final String COLUMN_SIZE = ColumnSize.MD_4 + "," + ColumnSize.LG_4 + "," + ColumnSize.SM_12 + "," + ColumnSize.XS_12;

    private FlowPanel contentPanel;

    private Presenter presenter;

    private final RaplaResources i18n;

    private final BundleManager bundleManager;

    private final RaplaLocale raplaLocale;

    // internal components
    private final DropDownInputField datesSelection;
    private final CheckBoxComponent allDayCheckBox;
    private final DateRangeComponent drp;
    private Reservation actualReservation;

    public ResourceDatesView(RaplaResources i18n, BundleManager bundleManager, RaplaLocale raplaLocale)
    {
        this.i18n = i18n;
        this.bundleManager = bundleManager;
        this.raplaLocale = raplaLocale;
        drp = new DateRangeComponent(bundleManager, new DateRangeChangeListener()
        {
            @Override
            public void dateRangeChanged(Date startDate, Date endDate)
            {
                getPresenter().timeChanged(startDate, endDate);
            }
        });
        contentPanel = new FlowPanel();
        contentPanel.setStyleName("resourcesDates");
        Container container = new Container();
        Row datesRow = new Row();
        Div datesButtons = new Div();
        datesButtons.add(createButton(i18n.getString("clear"), IconType.TRASH_O, new ClickHandler()
        {
            @Override
            public void onClick(ClickEvent event)
            {
                getPresenter().deleteDateClicked();
            }
        }));
        datesButtons.add(createButton(i18n.getString("new"), IconType.PLUS_CIRCLE, new ClickHandler()
        {
            @Override
            public void onClick(ClickEvent event)
            {
                getPresenter().newDateClicked();
            }
        }));
        datesRow.add(datesButtons);
        container.add(datesRow);
        Collection<DropDownItem> values = new ArrayList<DropDownItem>();
        datesSelection = new DropDownInputField("dates", new DropDownValueChanged()
        {
            @Override
            public void valueChanged(String newValue)
            {
                int index = Integer.parseInt(newValue);
                Appointment[] appointments = actualReservation.getAppointments();
                Appointment selectedAppointment = appointments[index];
                getPresenter().selectedAppointment(selectedAppointment);
                // Update
            }
        }, values);
        allDayCheckBox = new CheckBoxComponent("all day", new CheckBoxChangeListener()
        {
            @Override
            public void changed(boolean selected)
            {
                getPresenter().allDayEvent(selected);
            }
        });
        drp.setWithTime(true);
        final Row row1 = new Row();
        container.add(row1);
        Column column1 = new Column(COLUMN_SIZE);
        column1.add(datesSelection);
        row1.add(column1);
        Column column2 = new Column(COLUMN_SIZE);
        column2.add(drp);
        row1.add(column2);
        Column column3 = new Column(COLUMN_SIZE);
        column3.add(allDayCheckBox);
        row1.add(column3);
        contentPanel.add(container);
        // Just for testing
        contentPanel.add(new ClockPicker(new Date(), new TimeChangeListener()
        {
            @Override
            public void timeChanged(Date newDate)
            {
            }
        }, bundleManager));

    }

    @Override
    public void setPresenter(Presenter presenter)
    {
        this.presenter = presenter;
    }

    protected Presenter getPresenter()
    {
        return presenter;
    }

    @Override
    public Widget provideContent()
    {
        return contentPanel;
    }

    @Override
    public void updateAppointments(Appointment[] allAppointments, Appointment selectedAppointment)
    {
        Collection<DropDownItem> values = new ArrayList<DropDownItem>();
        for (int i = 0; i < allAppointments.length; i++)
        {
            Appointment appointment = allAppointments[i];
            values.add(new DropDownItem(formatDate(appointment), i + "", appointment == selectedAppointment));
        }
        datesSelection.changeSelection(values);
        updateDateRangeComponent(selectedAppointment);
        boolean isAllDay = selectedAppointment != null ? selectedAppointment.isWholeDaysSet() : false;
        allDayCheckBox.setValue(isAllDay, false);
    }

    @Override
    public void createContent(final Reservation reservation)
    {
        this.actualReservation = reservation;
        final Appointment[] appointments = reservation.getAppointments();
        //
        Collection<DropDownItem> values = new ArrayList<DropDownItem>();
        boolean first = true;
        for (int i = 0; i < appointments.length; i++)
        {
            Appointment appointment = appointments[i];
            values.add(new DropDownItem(formatDate(appointment), i + "", first));
            first = false;
        }
        datesSelection.changeSelection(values);
        Appointment selectedAppointment = appointments.length > 0 ? appointments[0] : null;
        updateDateRangeComponent(selectedAppointment);
    }

    private void updateDateRangeComponent(Appointment selectedAppointment)
    {
        Date start = selectedAppointment != null ? selectedAppointment.getStart() : null;
        Date end = selectedAppointment != null ? selectedAppointment.getEnd() : null;
        boolean holeDay = selectedAppointment != null ? selectedAppointment.isWholeDaysSet() : false;
        drp.updateStartEnd(start, end, holeDay);
    }

    private Button createButton(String text, IconType icon, ClickHandler clickHandler)
    {
        Button button = new Button();
        button.setIcon(icon);
        button.setText(text);
        button.addClickHandler(clickHandler);
        return button;
    }

    private String formatDate(Appointment appointment)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(raplaLocale.formatDateLong(appointment.getStart()));
        //        sb.append(" - ");
        //        sb.append(raplaLocale.formatDateLong(appointment.getEnd()));
        String dateString = sb.toString();
        return dateString;
    }

    public void clearContent()
    {
        contentPanel.clear();
    }

    public void update(Reservation reservation)
    {
        createContent(reservation);
    }
}
