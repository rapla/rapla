package org.rapla.client.gwt.components;

import java.util.Date;

import org.gwtbootstrap3.extras.datetimepicker.client.ui.DateTimePicker;
import org.gwtbootstrap3.extras.datetimepicker.client.ui.base.constants.DateTimePickerLanguage;
import org.rapla.client.gwt.components.DateComponent.DateValueChanged;
import org.rapla.components.i18n.BundleManager;
import org.rapla.framework.RaplaLocale;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;

public class DateTimeComponent extends FlowPanel
{
    private final DateTimePicker dateTimePicker;

    public DateTimeComponent(String textLabel, BundleManager bundleManager, Date initDate, RaplaLocale locale, final DateValueChanged changeHandler)
    {
        setStyleName("dateInfoLineComplete");

        Label beginText = new Label(textLabel);
        beginText.setStyleName("textlabel");
        add(beginText);

        final FlowPanel wrapper = new FlowPanel();
        dateTimePicker = new DateTimePicker();
        wrapper.add(dateTimePicker);
        wrapper.addStyleName("timePicker");
        final DateTimePickerLanguage lang = DateTimePickerLanguage.valueOf(locale.getLocale().getLanguage().toUpperCase());
        dateTimePicker.setLanguage(lang);
        dateTimePicker.setFormat(bundleManager.getFormats().getFormatDateShort().toLowerCase() + " HH:ii");
        dateTimePicker.setValue(initDate);
        add(wrapper);

        final Label beginTimeText = new Label("Uhr");
        beginTimeText.setStyleName("textlabel");
        add(beginTimeText);
    }

    public void setDate(Date date)
    {
        dateTimePicker.setValue(date);
    }

}
