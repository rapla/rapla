package org.rapla.client.gwt.components;

import org.gwtbootstrap3.client.ui.InputGroup;
import org.gwtbootstrap3.client.ui.InputGroupAddon;
import org.gwtbootstrap3.client.ui.TextBox;

import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.user.client.ui.FlowPanel;

public class TextInputField extends FlowPanel
{
    public interface TextValueChanged
    {
        void valueChanged(String newValue);
    }

    public TextInputField(final String labelText, String valueText, final TextValueChanged changeHandler)
    {
        this(labelText, valueText, null, changeHandler);
    }

    public TextInputField(final String labelText, String valueText, String placeholder, final TextValueChanged changeHandler)
    {
        super();
        setStyleName("textInput inputWrapper");
        final InputGroup inputGroup = new InputGroup();
        final InputGroupAddon addon = new InputGroupAddon();
        addon.setText(labelText);
        inputGroup.add(addon);
        final TextBox tb = new TextBox();
        tb.addChangeHandler(new ChangeHandler()
        {
            @Override
            public void onChange(ChangeEvent event)
            {
                final String newValue = tb.getValue();
                changeHandler.valueChanged(newValue);
            }
        });
        tb.setPlaceholder(placeholder);
        tb.setAutoComplete(false);
        tb.setValue(valueText, false);
        inputGroup.add(tb);
        add(inputGroup);
    }

}
