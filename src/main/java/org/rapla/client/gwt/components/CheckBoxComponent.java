package org.rapla.client.gwt.components;

import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import org.gwtbootstrap3.client.ui.CheckBox;

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
        addChangeHandler(event -> {
            boolean selected = getValue();
            listener.changed(selected);
        });
    }

}
