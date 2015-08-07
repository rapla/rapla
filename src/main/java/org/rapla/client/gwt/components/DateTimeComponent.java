package org.rapla.client.gwt.components;

import java.util.Date;

import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.i18n.client.DateTimeFormat.PredefinedFormat;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.datepicker.client.DateBox;

public class DateTimeComponent extends FlowPanel
{
    private final DateBox dateBegin;

    public DateTimeComponent(String textLabel)
    {
        DateTimeFormat dateFormat = DateTimeFormat.getFormat(PredefinedFormat.DATE_MEDIUM);
        final DateBox.DefaultFormat format = new DateBox.DefaultFormat(dateFormat);
        setStyleName("dateInfoLineComplete");

        Label beginText = new Label(textLabel);
        beginText.setStyleName("label");
        add(beginText);

        dateBegin = new DateBox();
        dateBegin.setValue(new Date(System.currentTimeMillis()));
        dateBegin.setStyleName("dateInput");
        dateBegin.setFormat(format);
        add(dateBegin);

        //        timeBegin = new SmallTimeBox(new Date(-3600000));
        //        begin.add(timeBegin);
        final Label beginTimeText = new Label("Uhr");
        beginTimeText.setStyleName("label");
        add(beginTimeText);
    }

    public void setDate(Date date)
    {
        dateBegin.setValue(date, false);
    }

}
