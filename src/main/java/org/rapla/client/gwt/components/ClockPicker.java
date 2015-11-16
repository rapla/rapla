package org.rapla.client.gwt.components;

import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.user.client.ui.TextBox;
import jsinterop.annotations.JsFunction;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;
import org.gwtbootstrap3.client.ui.InputGroupAddon;
import org.gwtbootstrap3.client.ui.constants.IconType;
import org.gwtbootstrap3.client.ui.html.Div;
import org.rapla.client.gwt.components.util.JQueryElement;
import org.rapla.client.gwt.components.util.JS;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.I18nLocaleFormats;

import java.util.Date;

public class ClockPicker extends Div
{
    public interface TimeChangeListener
    {
        void timeChanged(final Date newDate);
    }

    @JsType(isNative = true)
    public static interface ClockPickerJquery extends JQueryElement
    {
        public ClockPickerElement clockpicker(ClockPickerOptions options);

        public void remove();
    }

    @JsType(isNative = true)
    public interface ClockPickerElement extends JQueryElement
    {
        /*
         * clockpicker is the key
         */
        public ClockPickerI data(String key);
    }

    @JsType(isNative = true)
    public interface ClockPickerI extends JQueryElement
    {
        public void show();
    }

    @JsFunction
    public interface Callback
    {
        public void handleAction();
    }

    @JsType(isNative = true)
    public interface ClockPickerOptions
    {
        @JsProperty
        public void setAutoclose(Boolean autoclose);

        @JsProperty
        public Boolean getAutoclose();

        @JsProperty
        public void setTwelvehour(Boolean twelvehour);

        @JsProperty
        public Boolean getTwelvehour();

        @JsProperty
        public void setAfterDone(Callback listener);

        @JsProperty
        public Callback getAfterDone();
    }

    private ClockPickerI clockPicker;
    private final TimeChangeListener changeListener;
    private final DateTimeFormat format;
    private final TextBox input = new TextBox();
    private final boolean amPmFormat;

    public ClockPicker(final Date initDate, final TimeChangeListener changeListener, final BundleManager bundleManager)
    {
        this.changeListener = changeListener;
        amPmFormat = bundleManager.getFormats().isAmPmFormat();
        setStyleName("raplaClockPicker input-group clockpicker");
        final I18nLocaleFormats formats = bundleManager.getFormats();
        final String formatHour = formats.getFormatHour();
        format = DateTimeFormat.getFormat(formatHour);
        input.setStyleName("form-control");
        input.addChangeHandler(new ChangeHandler()
        {
            @Override
            public void onChange(ChangeEvent event)
            {
                timeChanged();
            }
        });
        setTime(initDate);
        add(input);
        final InputGroupAddon addon = new InputGroupAddon();
        addon.setIcon(IconType.CLOCK_O);
        addon.addDomHandler(new ClickHandler()
        {
            @Override
            public void onClick(ClickEvent event)
            {
                clockPicker.show();
                event.stopPropagation();
            }
        }, ClickEvent.getType());
        add(addon);
    }

    public void setTime(final Date time)
    {
        input.setValue(format.format(time));
    }

    @Override
    protected void onAttach()
    {
        super.onAttach();
        ClockPickerJquery jqe = (ClockPickerJquery) JQueryElement.Static.$(input.getElement());
        ClockPickerOptions options = JS.createObject();
        options.setAutoclose(true);
        options.setTwelvehour(amPmFormat);
        options.setAfterDone(new Callback()
        {
            @Override
            public void handleAction()
            {
                timeChanged();
            }
        });
        ClockPickerElement clockPickerElement = jqe.clockpicker(options);
        clockPicker = clockPickerElement.data("clockpicker");
    }

    private void timeChanged()
    {
        final String value = input.getValue();
        final Date time = format.parse(value);
        changeListener.timeChanged(time);
    }

    public Date getTime()
    {
        final String value = input.getValue();
        final Date time = format.parse(value);
        return time;
    }


}
