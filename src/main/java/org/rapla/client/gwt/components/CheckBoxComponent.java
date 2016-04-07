package org.rapla.client.gwt.components;

import org.gwtbootstrap3.client.ui.CheckBox;

import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;

public class CheckBoxComponent extends CheckBox
{
    public interface CheckBoxChangeListener
    {
        void changed(boolean selected);
    }

    public CheckBoxComponent(final String label, final CheckBoxChangeListener listener)
    {
        super();
        addStyleName("inputWrapper");
        setHTML(label);
        addChangeHandler(new ChangeHandler()
        {

            @Override
            public void onChange(ChangeEvent event)
            {
                boolean selected = getValue();
                listener.changed(selected);
            }
        });
    }

}
