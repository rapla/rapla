package org.rapla.client.gwt.components;

import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.regexp.shared.RegExp;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.TextBox;
import org.gwtbootstrap3.client.ui.InputGroup;
import org.gwtbootstrap3.client.ui.InputGroupAddon;
import org.gwtbootstrap3.client.ui.constants.Styles;

public class LongInputField extends FlowPanel
{
    public interface LongValueChange
    {
        void valueChanged(Long newValue);
    }

    public LongInputField(final String label, Long value, final LongValueChange changeHandler)
    {
        this(label, value, null, changeHandler);
    }

    public LongInputField(final String label, Long value, final String placeholder, final LongValueChange changeHandler)
    {
        super();
        setStyleName("integerInput inputWrapper");
        final InputGroup inputGroup = new InputGroup();
        final InputGroupAddon addon = new InputGroupAddon();
        addon.setText(label);
        inputGroup.add(addon);
        final TextBox input = new TextBox();
        input.setStyleName(Styles.FORM_CONTROL);
        input.getElement().setAttribute("type", "number");
        input.addKeyUpHandler(new KeyUpHandler()
        {

            @Override
            public void onKeyUp(KeyUpEvent event)
            {
                final String valueAsString = input.getValue();
                if (valueAsString != null && !valueAsString.isEmpty())
                {
                    final Integer intValue = parseInteger(valueAsString);
                    input.setValue(intValue + "");
                }
            }

            private Integer parseInteger(String valueAsString)
            {
                boolean negativ = valueAsString.startsWith("-");
                final String replaced = valueAsString.replaceAll("-", "");
                final String valueOnlyDigits = RegExp.compile("[^0-9]").replace(replaced, "");
                Integer valueAsInt = new Integer(valueOnlyDigits);
                if (negativ)
                {
                    valueAsInt *= -1;
                }
                return valueAsInt;
            }
        });
        input.addChangeHandler(event -> {
            final String valueAsString = input.getValue();
            final Integer newValue = valueAsString != null ? new Integer(valueAsString) : null;
            changeHandler.valueChanged(newValue != null ? new Long(newValue) : newValue);
        });
        inputGroup.add(input);
        this.add(inputGroup);
    }
}
