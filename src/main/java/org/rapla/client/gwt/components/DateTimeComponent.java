package org.rapla.client.gwt.components;

import java.util.Date;

import org.rapla.client.gwt.components.DateComponent.DateValueChanged;
import org.rapla.components.i18n.BundleManager;
import org.rapla.framework.RaplaLocale;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;

public class DateTimeComponent extends FlowPanel
{
    private final DateComponent datePicker;

    public DateTimeComponent(String textLabel, BundleManager bundleManager, Date initDate, RaplaLocale locale, final DateValueChanged changeHandler)
    {
        setStyleName("dateInfoLineComplete");

        Label beginText = new Label(textLabel);
        beginText.setStyleName("label");
        add(beginText);

        datePicker = new DateComponent(initDate, locale, new DateValueChanged()
        {
            @Override
            public void valueChanged(Date newValue)
            {
                setDate(newValue);
                changeHandler.valueChanged(newValue);
            }
        }, bundleManager);
        add(datePicker);

        final Label beginTimeText = new Label("Uhr");
        beginTimeText.setStyleName("label");
        add(beginTimeText);
    }

    public void setDate(Date date)
    {
        datePicker.setDate(date);
    }

}
