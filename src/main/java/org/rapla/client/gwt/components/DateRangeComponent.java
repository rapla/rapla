package org.rapla.client.gwt.components;

import java.util.Date;

import org.gwtbootstrap3.client.ui.Input;
import org.gwtbootstrap3.client.ui.constants.InputType;
import org.rapla.client.gwt.components.util.JQueryElement;

import com.google.gwt.core.client.js.JsProperty;
import com.google.gwt.core.client.js.JsType;

public class DateRangeComponent extends Input
{

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
        int isTimePickerIncrement();

        @JsProperty
        Locale getLocale();

        void controlChanged();

        void setStartDate(Date start);

        void setEndDate(Date end);
    }

    @JsType
    public interface Locale
    {
        @JsProperty
        void setFormat(String format);

        @JsProperty
        String getFormat();
    }

    public DateRangeComponent()
    {
        super(InputType.TEXT);
    }

    @Override
    protected void onAttach()
    {
        super.onAttach();
        DateRangePickerJquery jqdp = (DateRangePickerJquery) JQueryElement.Static.$(getElement());
        DateRangePickerElement ele = jqdp.daterangepicker();
        DateRangePicker datePicker = ele.data("daterangepicker");
        datePicker.setShowWeekNumbers(true);
        datePicker.setAutoApply(true);
        datePicker.setTimePicker(true);
        datePicker.setTimePickerIncrement(30);
        datePicker.getLocale().setFormat("MM/DD/YYYY h:mm A");
        datePicker.controlChanged();
    }
}
