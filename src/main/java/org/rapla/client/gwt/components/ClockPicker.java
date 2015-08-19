package org.rapla.client.gwt.components;

import java.util.Date;

import org.rapla.components.i18n.BundleManager;

import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.TextBox;

public class ClockPicker extends FlowPanel
{
    public interface TimeChangeListener
    {
        void timeChanged(final Date newDate);
    }

    private static int counter = 0;
    private final TimeChangeListener changeListener;
    private final DateTimeFormat format;
    private final TextBox input = new TextBox();

    public ClockPicker(final Date initDate, final TimeChangeListener changeListener, final BundleManager bundleManager)
    {
        this.changeListener = changeListener;
        setStyleName("raplaClockPicker input-group clockpicker");
        final String formatHour = bundleManager.getFormats().getFormatHour();
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
        final String id = "clockpicker-" + counter;
        counter++;
        input.getElement().setId(id);
        input.setValue(format.format(initDate));
        add(input);
        input.addFocusHandler(new FocusHandler()
        {
            @Override
            public void onFocus(FocusEvent event)
            {
                showClockPicker(id, ClockPicker.this);
            }
        });
        final Element span = DOM.createSpan();
        span.setClassName("input-group-addon");
        getElement().appendChild(span);
        final Element innerSpan = DOM.createSpan();
        innerSpan.setClassName("glyphicon glyphicon-time");
        span.appendChild(innerSpan);
        DOM.sinkEvents(span, Event.ONCLICK | Event.ONTOUCHEND);
        addHandler(new ClickHandler()
        {
            @Override
            public void onClick(ClickEvent event)
            {
                event.stopPropagation();
                showClockPicker(id, ClockPicker.this);
            }
        }, ClickEvent.getType());
    }

    private void timeChanged()
    {
        final String value = input.getValue();
        final Date time = format.parse(value);
        changeListener.timeChanged(time);
    }

    public native void showClockPicker(final String id, final ClockPicker cp)
    /*-{
         $wnd.$('#'+id).clockpicker({
             autoclose: true,
             afterDone: function(){
                 cp.@org.rapla.client.gwt.components.ClockPicker::timeChanged()();
             }
         }).clockpicker('show');
     }-*/;

}
