package org.rapla.client.gwt.components;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import org.gwtbootstrap3.client.ui.ButtonGroup;
import org.gwtbootstrap3.client.ui.RadioButton;
import org.gwtbootstrap3.client.ui.constants.Toggle;

import java.util.ArrayList;
import java.util.List;

public class ButtonGroupComponent extends ButtonGroup
{
    public interface ButtonGroupSelectionChangeListener
    {
        void selectionChanged(String id);
    }

    public static class ButtonGroupEntry
    {
        private final String label;
        private final String id;

        public ButtonGroupEntry(String label, String id)
        {
            super();
            this.label = label;
            this.id = id;
        }

        public String getLabel()
        {
            return label;
        }

        public String getId()
        {
            return id;
        }
    }

    private List<RadioButton> buttons = new ArrayList<>();

    public ButtonGroupComponent(ButtonGroupEntry[] entries, String groupId, final ButtonGroupSelectionChangeListener listener)
    {
        addStyleName("inputWrapper");
        setDataToggle(Toggle.BUTTONS);
        for (ButtonGroupEntry entry : entries)
        {
            RadioButton button = new RadioButton(groupId, entry.getLabel());
            button.setId(entry.getId());
            buttons.add(button);
            add(button);
        }
        addDomHandler(event -> {
            Element target = DOM.eventGetTarget((Event) event.getNativeEvent());
            for (RadioButton button : buttons)
            {
                if (button.getElement() == target)
                {
                    String id = button.getId();
                    listener.selectionChanged(id);
                }
            }
        }, ClickEvent.getType());
    }

    public void setDisabled(String[] ids)
    {
        for (RadioButton radioButton : buttons)
        {
            boolean enabled = true;
            for (String label : ids)
            {
                if (label.equals(radioButton.getId()))
                {
                    enabled = false;
                    break;
                }
            }
            radioButton.setEnabled(enabled);
        }
    }

    public void setSelected(String id)
    {
        for (RadioButton radioButton : buttons)
        {
            if (radioButton.getId().equals(id))
            {
                radioButton.setActive(true);
            }
            else
            {
                radioButton.setActive(false);
            }
        }
    }

}
