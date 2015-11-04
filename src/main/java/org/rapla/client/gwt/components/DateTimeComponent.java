package org.rapla.client.gwt.components;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.gwtbootstrap3.extras.datetimepicker.client.ui.DateTimePicker;
import org.gwtbootstrap3.extras.datetimepicker.client.ui.base.constants.DateTimePickerLanguage;
import org.gwtbootstrap3.extras.datetimepicker.client.ui.base.constants.DateTimePickerView;
import org.gwtbootstrap3.extras.datetimepicker.client.ui.base.events.ChangeDateEvent;
import org.gwtbootstrap3.extras.datetimepicker.client.ui.base.events.ChangeDateHandler;
import org.rapla.client.gwt.components.DateComponent.DateValueChanged;
import org.rapla.client.gwt.components.util.GWTDateUtils;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.I18nLocaleFormats;
import org.rapla.components.util.DateTools;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;

public class DateTimeComponent extends FlowPanel
{
    private static final Map<Character, Character> DATE_TIME_FORMAT_MAP = new HashMap<Character, Character>();

    static
    {
        DATE_TIME_FORMAT_MAP.put('H', 'h'); // 12/24 hours
        DATE_TIME_FORMAT_MAP.put('h', 'H'); // 12/24 hours
        DATE_TIME_FORMAT_MAP.put('M', 'm'); // months
        DATE_TIME_FORMAT_MAP.put('m', 'i'); // minutes
        DATE_TIME_FORMAT_MAP.put('a', 'P'); // meridian
    }

    private final DateTimePicker datePicker;
    private final DateTimePicker timePicker;

    public DateTimeComponent(String textLabel, BundleManager bundleManager, Date initDate, final DateValueChanged changeHandler)
    {
        setStyleName("dateInfoLineComplete");

        Label beginText = new Label(textLabel);
        beginText.setStyleName("textlabel");
        add(beginText);

        final FlowPanel wrapper = new FlowPanel();
        datePicker = new DateTimePicker();
        wrapper.add(datePicker);
        wrapper.addStyleName("timePicker");

        final String language = DateTools.getLang(bundleManager.getLocale());
        DateTimePickerLanguage lang = null;
        for (DateTimePickerLanguage l : DateTimePickerLanguage.values())
        {
            if (l.name().equalsIgnoreCase(language))
            {
                lang = l;
            }
        }
        if ( lang != null)
        {
            datePicker.setLanguage(lang);
        }

        final I18nLocaleFormats formats = bundleManager.getFormats();
        final String format = formats.getFormatDateShort();
        final String convertedFormat = convertFormat(format);
        datePicker.setFormat(convertedFormat);
        datePicker.setAutoClose(true);
        datePicker.setMinView(DateTimePickerView.MONTH);
        datePicker.setStartView(DateTimePickerView.MONTH);
        add(wrapper);
        timePicker = new DateTimePicker();
        final boolean amPmFormat = formats.isAmPmFormat();
        final String timeFormat = formats.getFormatHour();
        final String convertedTimeFormat = convertFormat(timeFormat);
        timePicker.setLanguage(lang);
        timePicker.setShowMeridian(amPmFormat);
        timePicker.setFormat(convertedTimeFormat);
        timePicker.setMinView(DateTimePickerView.HOUR);
        timePicker.setStartView(DateTimePickerView.DAY);
        timePicker.setMaxView(DateTimePickerView.DAY);
        timePicker.setMinuteStep(10);
        timePicker.setAutoClose(true);
        timePicker.addChangeDateHandler(new ChangeDateHandler()
        {
            @Override
            public void onChangeDate(ChangeDateEvent evt)
            {
                final Date date = datePicker.getValue();
                final Date time = timePicker.getValue();
                final Date result = GWTDateUtils.gwtDateTimeToRapla(date, time);
                changeHandler.valueChanged(result);
            }
        });
        wrapper.add(timePicker);
        datePicker.addChangeDateHandler(new ChangeDateHandler()
        {
            @Override
            public void onChangeDate(ChangeDateEvent evt)
            {
                final Date date = datePicker.getValue();
                final Date time = timePicker.getValue();
                final Date result = GWTDateUtils.gwtDateTimeToRapla(date, time);
                changeHandler.valueChanged(result);
            }
        });
        if (initDate != null)
        {
            setDate(initDate);
        }
    }

    private String convertFormat(String format)
    {
        final StringBuilder fb = new StringBuilder(format);
        for (int i = 0; i < fb.length(); i++)
        {
            if (DATE_TIME_FORMAT_MAP.containsKey(fb.charAt(i)))
            {
                fb.setCharAt(i, DATE_TIME_FORMAT_MAP.get(fb.charAt(i)));
            }
        }
        return fb.toString();
    }

    public void setDate(Date date)
    {
        final Date gwtDate = GWTDateUtils.raplaToGwtDate(date);
        datePicker.setValue(gwtDate);
        final Date gwtTime = GWTDateUtils.raplaToGwtDateTime(date);
        timePicker.setValue(gwtTime);
    }

}
