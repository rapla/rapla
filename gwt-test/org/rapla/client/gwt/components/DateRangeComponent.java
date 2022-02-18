package org.rapla.client.gwt.components;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONString;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;
import org.gwtbootstrap3.client.ui.Input;
import org.gwtbootstrap3.client.ui.constants.InputType;
import org.rapla.RaplaResources;
import org.rapla.client.gwt.components.util.Function;
import org.rapla.client.gwt.components.util.GWTDateUtils;
import org.rapla.client.gwt.components.util.JQueryElement;
import org.rapla.client.gwt.components.util.JS;
import org.rapla.client.gwt.components.util.JqEvent;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.I18nLocaleFormats;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class DateRangeComponent extends Input
{
    public interface DateRangeChangeListener
    {
        void dateRangeChanged(Date startDate, Date endDate);
    }

    @JsType
    public interface DateRangePickerJquery extends JQueryElement
    {
        DateRangePickerElement daterangepicker(DateRangeOptions options);
    }

    @JsType
    public interface DateRangePickerElement extends JQueryElement
    {
        DateRangePicker data(String key);
    }

    @JsType
    public interface DateRangePicker extends JQueryElement
    {
        @JsProperty void setTimePicker(boolean timePicker);

        @JsProperty boolean isTimePicker();

        @JsProperty Locale getLocale();

        void setStartDate(Date start);

        void setEndDate(Date end);

        void updateView();
        
        void remove();
    }

    @JsType
    public interface DateRangeOptions
    {
        @JsProperty void setLocale(Locale locale);

        @JsProperty Locale getLocale();

        @JsProperty void setTimePicker(boolean timePicker);

        @JsProperty
        boolean isTimePicker();

        @JsProperty
        void setTimePicker24Hour(boolean timePicker);

        @JsProperty
        boolean isTimePicker24Hour();

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

    }

    @JsType
    public interface Locale
    {
        @JsProperty
        void setFormat(String format);

        @JsProperty
        String getFormat();

        @JsProperty
        void setFirstDay(int firstDay);

        @JsProperty
        int getFirstDay();

        @JsProperty
        void setDaysOfWeek(JavaScriptObject daysOfWeek);

        @JsProperty
        JavaScriptObject getDaysOfWeek();

        @JsProperty
        void setMonthNames(JavaScriptObject monthNames);

        @JsProperty
        JavaScriptObject getMonthNames();

        @JsProperty
        void setApplyLabel(String applyLabel);

        @JsProperty
        String getApplyLabel();

        @JsProperty
        void setCancelLabel(String cancelLabel);

        @JsProperty
        String getCancelLabel();
    }

    private static final Map<Character, Character> DATE_TIME_FORMAT_MAP = new HashMap<>();

    static
    {
        DATE_TIME_FORMAT_MAP.put('y', 'Y');
        DATE_TIME_FORMAT_MAP.put('d', 'D');
        DATE_TIME_FORMAT_MAP.put('a', 'A');
    }

    private DateRangePicker datePicker = null;
    private boolean withTime;
    private final BundleManager bundleManager;
    private final DateRangeChangeListener changeListener;
    private final RaplaResources i18n;

    public DateRangeComponent(BundleManager bundleManager, RaplaResources i18n, DateRangeChangeListener changeListener)
    {
        super(InputType.TEXT);
        this.bundleManager = bundleManager;
        this.changeListener = changeListener;
        this.i18n = i18n;
        addStyleName("inputWrapper");
        addValueChangeHandler(event -> update());
    }

    private void update()
    {
        String newValue = getValue();
        if (newValue != null)
        {
            String[] split = newValue.split("-");
            DateTimeFormat dateTimeFormat = getDateTimeFormat();
            Date start = dateTimeFormat.parse(split[0].trim());
            Date end = dateTimeFormat.parse(split[1].trim());
            start = GWTDateUtils.gwtDateTimeToRapla(start, start);
            end = GWTDateUtils.gwtDateTimeToRapla(end, end);
            changeListener.dateRangeChanged(start, end);
        }
    }

    @Override
    protected void onAttach()
    {
        super.onAttach();
        DateRangeOptions options = JS.createObject();
        options.setShowWeekNumbers(true);
        options.setAutoApply(false);
        I18nLocaleFormats formats = bundleManager.getFormats();
        options.setTimePicker(withTime);
        options.setTimePicker24Hour(!formats.isAmPmFormat());
        options.setTimePickerIncrement(5);
        options.setLocale((Locale)JS.createObject());
        Locale locale = options.getLocale();
        locale.setFirstDay(1);
        locale.setApplyLabel(i18n.getString("apply"));
        locale.setCancelLabel(i18n.getString("cancel"));
        locale.setMonthNames(createJavaScriptArray(formats.getMonths()));
        locale.setDaysOfWeek(createJavaScriptArray(formats.getShortWeekdays()));
        locale.setFormat(getFormat(withTime));
        DateRangePickerJquery jqdp = (DateRangePickerJquery) JQueryElement.Static.$(getElement());
        DateRangePickerElement ele = jqdp.daterangepicker(options);
        ele.on("apply.daterangepicker", (event, params) -> {
            update();
            return null;
        });
        datePicker = ele.data("daterangepicker");
    }
    
    @Override
    protected void onDetach()
    {
        super.onDetach();
        datePicker.remove();
        datePicker = null;
    }

    private JavaScriptObject createJavaScriptArray(String[] strings)
    {
        final JSONArray jsonArray = new JSONArray();
        if (strings != null)
        {
            int index = 0;
            for (String string : strings)
            {
                if (string != null)
                {
                    jsonArray.set(index, new JSONString(string));
                    index++;
                }
            }
        }
        return jsonArray.getJavaScriptObject();
    }

    public void setWithTime(boolean withTime)
    {
        boolean oldWithTime = this.withTime;
        this.withTime = withTime;
        if (datePicker != null && oldWithTime != this.withTime)
        {
            datePicker.setTimePicker(this.withTime);
            String format = getFormat(this.withTime);
            datePicker.getLocale().setFormat(format);
            datePicker.updateView();
        }
    }

    private String convertToJsFormat(String format)
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

    public String getFormat(boolean withTime)
    {
        final String formatPattern = getFormatPattern(withTime);
        String jsFormat = convertToJsFormat(formatPattern);
        return jsFormat;
    }

    public void updateStartEnd(Date start, Date end, boolean holeDay)
    {
        setWithTime(!holeDay);
        start = GWTDateUtils.raplaToGwtDateTime(start);
        end = GWTDateUtils.raplaToGwtDateTime(end);
        DateTimeFormat format = getDateTimeFormat();
        setValue((start != null ? format.format(start) : "") + " - " + (end != null ? format.format(end) : ""), false);
        if (datePicker != null)
        {
            datePicker.setStartDate(start);
            datePicker.setEndDate(end);
        }
    }

    public DateTimeFormat getDateTimeFormat()
    {
        final String pattern = getFormatPattern(withTime);
        DateTimeFormat format = DateTimeFormat.getFormat(pattern);
        return format;
    }

    public String getFormatPattern(boolean withTime)
    {
        I18nLocaleFormats formats = bundleManager.getFormats();
        final String pattern = withTime ? formats.getFormatDateShort() + " " + formats.getFormatHour() + (formats.isAmPmFormat() ? " A" : "")
                : formats.getFormatDateShort();
        return pattern;
    }
}
