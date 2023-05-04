package org.rapla.client.gwt.components;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlowPanel;
import org.gwtbootstrap3.client.ui.InputGroup;
import org.gwtbootstrap3.client.ui.InputGroupAddon;
import org.gwtbootstrap3.client.ui.constants.Styles;

public class BooleanInputField extends FlowPanel
{
    public interface BooleanValueChange
    {
        void valueChanged(Boolean newValue);
    }

    public BooleanInputField(final String label, Boolean value, final BooleanValueChange changeHandler)
    {
        super();
        setStyleName("integerInput inputWrapper");
        final InputGroup inputGroup = new InputGroup();
        final InputGroupAddon addon = new InputGroupAddon();
        addon.setText(label);
        inputGroup.add(addon);
        final CheckBox cb = new CheckBox();
        cb.setStyleName(Styles.FORM_CONTROL);
        cb.setValue(value);
        cb.addClickHandler(event -> changeHandler.valueChanged(cb.getValue()));
        inputGroup.add(cb);
        add(inputGroup);
    }
}
