package org.rapla.client.gwt.components;

import java.util.Date;

import org.gwtbootstrap3.client.ui.Input;
import org.gwtbootstrap3.client.ui.constants.InputType;
import org.rapla.client.gwt.components.util.JQueryElement;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.I18nLocaleFormats;

import com.google.gwt.core.client.js.JsProperty;
import com.google.gwt.core.client.js.JsType;
import com.google.gwt.i18n.client.DateTimeFormat;

public class DateRangeComponent extends Input
{
    private DateRangePicker datePicker = null;
    private boolean withTime;
    private final BundleManager bundleManager;

    @JsType(prototype = "jQuery")
    public interface DateRangePickerJquery extends JQueryElement
    {
        DateRangePickerElement daterangepicker();

    }

    @JsType(prototype = "jQuery")
    public interface DateRangePickerElement extends JQueryElement
    {
        DateRangePicker data(String key);
    }

    @JsType(prototype = "DateRangePicker")
    public interface DateRangePicker extends JQueryElement
    {

        @JsProperty
        void setTimePicker(boolean timePicker);

        @JsProperty
        boolean isTimePicker();

        @JsProperty
        void setShowWeekNumbers(boolean showWeekNumbers);

        @JsProperty
        boolean isShowWeekNumbers();

        @JsProperty
        void setAutoApply(boolean autoApply);

        @JsProperty
        boolean isAutoApply();

        @JsProperty
        void setTimePickerIncrement(int timePickerIncrement);

        @JsProperty
        int getTimePickerIncrement();

        @JsProperty
        Locale getLocale();

        void setStartDate(Date start);

        void setEndDate(Date end);

        void updateView();
    }

    @JsType
    public interface Locale
    {
        @JsProperty
        void setFormat(String format);

        @JsProperty
        String getFormat();
    }

    public DateRangeComponent(BundleManager bundleManager)
    {
        super(InputType.TEXT);
        this.bundleManager = bundleManager;
        addStyleName("inputWrapper");
    }

    @Override
    protected void onAttach()
    {
        super.onAttach();
        DateRangePickerJquery jqdp = (DateRangePickerJquery) JQueryElement.Static.$(getElement());
        DateRangePickerElement ele = jqdp.daterangepicker();
        datePicker = ele.data("daterangepicker");
        reconfigure();
    }

    public void setWithTime(boolean withTime)
    {
        this.withTime = withTime;
        if (datePicker != null)
        {
            reconfigure();
        }
    }

    private void reconfigure()
    {
        datePicker.setShowWeekNumbers(true);
        datePicker.setAutoApply(false);
        datePicker.setTimePicker(withTime);
        datePicker.setTimePickerIncrement(30);
        String format = getFormat(withTime);
        updateTime(format);
        datePicker.getLocale().setFormat(format);
        datePicker.updateView();

    }

    public String getFormat(boolean withTime)
    {
        return withTime ? "MM/DD/YYYY h:mm A" : "MM/DD/YYYY";
    }
    
    private void updateTime(String format)
    {
        // TODO
        //        String value = getValue();
        //        String[] dates = value.split("-");
        //        String oldFormat = getFormat(!withTime);
    }

    public void updateStartEnd(Date start, Date end)
    {
        I18nLocaleFormats formats = bundleManager.getFormats();
        final String pattern = withTime ? formats.getFormatDateLong() + " " + formats.getFormatHour() + (formats.isAmPmFormat() ? " A" : "")
                : formats.getFormatDateLong();
        DateTimeFormat format = DateTimeFormat.getFormat(pattern);
        setText((start != null ? format.format(start) : "") + " - " + (end != null ? format.format(end) : ""));
    }
}
