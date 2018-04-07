package org.rapla.client.edit.reservation.sample.gwt.subviews;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.IsWidget;
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
import org.rapla.client.gwt.components.ButtonGroupComponent;
import org.rapla.client.gwt.components.ButtonGroupComponent.ButtonGroupEntry;
import org.rapla.client.gwt.components.ButtonGroupComponent.ButtonGroupSelectionChangeListener;
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
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaLocale;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

public class ResourceDatesView implements ReservationViewPart
{
    private static final String NO_REPEATING_ID = "no_repeating";
    private static final String COLUMN_2_SIZE = ColumnSize.MD_6 + "," + ColumnSize.LG_6 + "," + ColumnSize.SM_12 + "," + ColumnSize.XS_12;

    private final Div contentPanel;

    private final Presenter presenter;

    private final RaplaLocale raplaLocale;

    // internal components
    private final DropDownInputField datesSelection;
    private final CheckBoxComponent allDayCheckBox;
    private final DateRangeComponent drp;
    private final ButtonGroupComponent repeatingSelection;
    private Reservation actualReservation;
    private Appointment[] allAppointments;

    public ResourceDatesView(RaplaResources i18n, BundleManager bundleManager, RaplaLocale raplaLocale, Presenter presenter)
    {
        this.presenter = presenter;
        this.raplaLocale = raplaLocale;
        drp = new DateRangeComponent(bundleManager, i18n, (startDate, endDate) -> getPresenter().timeChanged(startDate, endDate));
        contentPanel = new Div();
        contentPanel.setStyleName("resourcesDates");
        Container container = new Container();
        Row datesRow = new Row();
        Div datesButtons = new Div();
        datesButtons.add(createButton(i18n.getString("clear"), IconType.TRASH_O, event -> getPresenter().deleteDateClicked()));
        datesButtons.add(createButton(i18n.getString("new"), IconType.PLUS_CIRCLE, event -> getPresenter().newDateClicked()));
        datesRow.add(datesButtons);
        container.add(datesRow);
        Collection<DropDownItem> values = new ArrayList<>();
        datesSelection = new DropDownInputField("dates", newValue -> {
            int index = Integer.parseInt(newValue);
            Appointment selectedAppointment = allAppointments[index];
            getPresenter().selectAppointment(selectedAppointment);
            // Update
        }, values);
        allDayCheckBox = new CheckBoxComponent("all day", selected -> getPresenter().allDayEvent(selected));
        drp.setWithTime(true);
        {
            final Row row1 = new Row();
            container.add(row1);
            Column column1 = new Column(ColumnSize.LG_3, ColumnSize.MD_3, ColumnSize.SM_12, ColumnSize.XS_12);
            column1.add(datesSelection);
            row1.add(column1);
            Column column2 = new Column(ColumnSize.LG_9, ColumnSize.MD_9, ColumnSize.SM_12, ColumnSize.XS_12);
            row1.add(column2);
            final ButtonGroupEntry[] labels = new ButtonGroupEntry[6];
            labels[0] = new ButtonGroupEntry(i18n.getString(NO_REPEATING_ID), NO_REPEATING_ID);
            labels[1] = new ButtonGroupEntry(i18n.getString(RepeatingType.WEEKLY.toString()), RepeatingType.WEEKLY.name());
            labels[2] = new ButtonGroupEntry(i18n.getString(RepeatingType.DAILY.toString()), RepeatingType.DAILY.name());
            labels[3] = new ButtonGroupEntry(i18n.getString(RepeatingType.MONTHLY.toString()), RepeatingType.MONTHLY.name());
            labels[4] = new ButtonGroupEntry(i18n.getString(RepeatingType.YEARLY.toString()), RepeatingType.YEARLY.name());
            labels[5] = new ButtonGroupEntry(i18n.getString("appointment.convert"), "convert");
            repeatingSelection = new ButtonGroupComponent(labels, "repeating", id -> {
                if (NO_REPEATING_ID.equals(id))
                {
                    getPresenter().repeating(null);
                }
                else if ("convert".equals(id))
                {
                    getPresenter().convertAppointment();
                }
                else
                {
                    RepeatingType repeating = RepeatingType.valueOf(id);
                    getPresenter().repeating(repeating);
                }
            });
            column2.add(repeatingSelection);
        }
        {
            Row row2 = new Row();
            Column column1 = new Column(COLUMN_2_SIZE);
            column1.add(drp);
            row2.add(column1);
            Column column2 = new Column(COLUMN_2_SIZE);
            column2.add(allDayCheckBox);
            row2.add(column2);
            container.add(row2);
        }
        contentPanel.add(container);
        // Just for testing
        contentPanel.add(new ClockPicker(new Date(), newDate -> {
        }, bundleManager));

    }

    protected Presenter getPresenter()
    {
        return presenter;
    }

    @Override
    public IsWidget provideContent()
    {
        return contentPanel;
    }

    @Override
    public void updateAppointments(Appointment[] allAppointments, Appointment selectedAppointment)
    {
        this.allAppointments = allAppointments;
        Collection<DropDownItem> values = new ArrayList<>();
        for (int i = 0; i < allAppointments.length; i++)
        {
            Appointment appointment = allAppointments[i];
            values.add(new DropDownItem(formatDate(appointment), i + "", appointment == selectedAppointment));
        }
        Repeating repeating = selectedAppointment.getRepeating();
        if (selectedAppointment.isRepeatingEnabled() && repeating != null)
        {
            RepeatingType type = repeating.getType();
            repeatingSelection.setSelected(type.name());
        }
        else
        {
            repeatingSelection.setSelected(NO_REPEATING_ID);
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
        allAppointments = reservation.getAppointments();
        //
        Collection<DropDownItem> values = new ArrayList<>();
        boolean first = true;
        for (int i = 0; i < allAppointments.length; i++)
        {
            Appointment appointment = allAppointments[i];
            values.add(new DropDownItem(formatDate(appointment), i + "", first));
            first = false;
        }
        datesSelection.changeSelection(values);
        Appointment selectedAppointment = allAppointments.length > 0 ? allAppointments[0] : null;
        if (selectedAppointment != null && selectedAppointment.isRepeatingEnabled() && selectedAppointment.getRepeating() != null)
        {
            Repeating repeating = selectedAppointment.getRepeating();
            RepeatingType type = repeating.getType();
            repeatingSelection.setSelected(type.name());
        }
        else
        {
            repeatingSelection.setSelected(NO_REPEATING_ID);
        }

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
