package org.rapla.client.gwt.components;

import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import org.gwtbootstrap3.client.ui.InputGroup;
import org.gwtbootstrap3.client.ui.InputGroupAddon;
import org.gwtbootstrap3.client.ui.ListBox;
import org.gwtbootstrap3.client.ui.constants.Styles;
import org.gwtbootstrap3.client.ui.html.Div;

import java.util.ArrayList;
import java.util.Collection;

public class DropDownInputField extends Div
{
    public interface DropDownValueChanged
    {
        void valueChanged(String newValue);
    }

    public static class DropDownItem
    {
        private final String name;
        private final String id;
        private final boolean selected;

        public DropDownItem(String name, String id, boolean selected)
        {
            super();
            this.name = name;
            this.id = id;
            this.selected = selected;
        }

        public boolean isSelected()
        {
            return selected;
        }

        public String getName()
        {
            return name;
        }

        public String getId()
        {
            return id;
        }

    }

    private final ListBox dropDown = new ListBox();

    public DropDownInputField(final String label, final DropDownValueChanged changeHandler, Collection<DropDownItem> values)
    {
        this(label, changeHandler, values, false);
    }

    public DropDownInputField(final String label, final DropDownValueChanged changeHandler, Collection<DropDownItem> values, boolean multiSelect)
    {
        super();
        setStyleName("dropDownInput inputWrapper");
        final InputGroup inputGroup = new InputGroup();
        final InputGroupAddon addon = new InputGroupAddon();
        inputGroup.add(addon);
        addon.setText(label);
        dropDown.setStyleName(Styles.FORM_CONTROL);
        dropDown.setMultipleSelect(multiSelect);
        ArrayList<Integer> selectedIndexes = new ArrayList<>();
        int index = 0;
        for (DropDownItem dropDownItem : values)
        {
            final String id = dropDownItem.getId();
            dropDown.addItem(dropDownItem.getName(), id);
            if(dropDownItem.isSelected())
            {
                selectedIndexes.add(index);
            }
            index++;
        }
        dropDown.addChangeHandler(event -> {
            String selected = dropDown.getSelectedValue();
            changeHandler.valueChanged(selected);
        });
        for (Integer selectedIndex : selectedIndexes)
        {
            dropDown.setSelectedIndex(selectedIndex);
        }
        inputGroup.add(dropDown);
        add(inputGroup);
    }

    public void changeSelection(Collection<DropDownItem> values)
    {
        dropDown.clear();
        ArrayList<Integer> selectedIndexes = new ArrayList<>();
        int index = 0;
        for (DropDownItem dropDownItem : values)
        {
            final String id = dropDownItem.getId();
            dropDown.addItem(dropDownItem.getName(), id);
            if(dropDownItem.isSelected())
            {
                selectedIndexes.add(index);
            }
            index++;
        }
        for (Integer selectedIndex : selectedIndexes)
        {
            dropDown.setSelectedIndex(selectedIndex);
        }

    }

}
