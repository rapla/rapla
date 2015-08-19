package org.rapla.client.gwt.components;

import java.util.Date;

import org.gwtbootstrap3.extras.datepicker.client.ui.DatePicker;
import org.gwtbootstrap3.extras.datepicker.client.ui.base.constants.DatePickerLanguage;
import org.gwtbootstrap3.extras.datepicker.client.ui.base.events.ChangeDateEvent;
import org.gwtbootstrap3.extras.datepicker.client.ui.base.events.ChangeDateHandler;
import org.rapla.client.gwt.components.ClockPicker.TimeChangeListener;
import org.rapla.client.gwt.components.DateComponent.DateValueChanged;
import org.rapla.client.gwt.components.util.GWTDateUtils;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.util.DateTools;
import org.rapla.framework.RaplaLocale;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;

public class DateTimeComponent extends FlowPanel
{
    private final DatePicker datePicker;
    private final ClockPicker clockPicker;

    public DateTimeComponent(String textLabel, BundleManager bundleManager, Date initDate, final RaplaLocale locale, final DateValueChanged changeHandler)
    {
        setStyleName("dateInfoLineComplete");

        Label beginText = new Label(textLabel);
        beginText.setStyleName("textlabel");
        add(beginText);

        final FlowPanel wrapper = new FlowPanel();
        datePicker = new DatePicker();
        wrapper.add(datePicker);
        wrapper.addStyleName("timePicker");
        final DatePickerLanguage lang = DatePickerLanguage.valueOf(locale.getLocale().getLanguage().toUpperCase());
        datePicker.setLanguage(lang);
        final String format = bundleManager.getFormats().getFormatDateShort().toLowerCase();
        datePicker.setFormat(format);
        datePicker.setValue(initDate);
        add(wrapper);
        clockPicker = new ClockPicker(initDate, new TimeChangeListener()
        {
            @Override
            public void timeChanged(Date time)
            {
                final Date date = datePicker.getValue();
                final Date result = GWTDateUtils.gwtDateTimeToRapla(date, time);
                changeHandler.valueChanged(result);
            }
        }, bundleManager);
        wrapper.add(clockPicker);
        datePicker.addChangeDateHandler(new ChangeDateHandler()
        {
            @Override
            public void onChangeDate(ChangeDateEvent evt)
            {
                final Date date = datePicker.getValue();
                final Date time = clockPicker.getTime();
                final Date result = GWTDateUtils.gwtDateTimeToRapla(date, time);
                changeHandler.valueChanged(result);
            }
        });

    }

    public void setDate(Date date)
    {
        final Date gwtDate = GWTDateUtils.raplaToGwtDate(date);
        datePicker.setValue(gwtDate);
        final Date gwtTime = GWTDateUtils.raplaToGwtTime(date);
        clockPicker.setTime(gwtTime);
    }

}
