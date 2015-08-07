package org.rapla.client.gwt.components;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.gwtbootstrap3.client.ui.InputGroup;
import org.gwtbootstrap3.client.ui.InputGroupAddon;
import org.gwtbootstrap3.client.ui.constants.Styles;

import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.ListBox;

public class DropDownInputField extends FlowPanel
{
    public interface DropDownValueChanged
    {
        void valueChanged(String newValue);
    }

    public static class DropDownItem
    {
        private final String name;
        private final String id;

        public DropDownItem(String name, String id)
        {
            this.name = name;
            this.id = id;
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

    public DropDownInputField(final String label, final DropDownValueChanged changeHandler, Collection<DropDownItem> values, String selectedId)
    {
        this(label, changeHandler, values, false, selectedId);
    }

    public DropDownInputField(final String label, final DropDownValueChanged changeHandler, Collection<DropDownItem> values, boolean multiSelect,
            final String... selectedId)
    {
        super();
        setStyleName("dropDownInput inputWrapper");
        final InputGroup inputGroup = new InputGroup();
        final InputGroupAddon addon = new InputGroupAddon();
        inputGroup.add(addon);
        addon.setText(label);
        final ListBox dropDown = new ListBox();
        dropDown.setStyleName(Styles.FORM_CONTROL);
        dropDown.setMultipleSelect(multiSelect);
        int index[] = new int[selectedId.length];
        int currentIndex = 0;
        String[] sorted = filterAndSort(selectedId);
        for (DropDownItem dropDownItem : values)
        {
            final String id = dropDownItem.getId();
            dropDown.addItem(dropDownItem.getName(), id);
            final int foundIndex = Arrays.binarySearch(sorted, id);
            index[foundIndex] = currentIndex;
            currentIndex++;
        }
        for (int ind : index)
        {
            dropDown.setSelectedIndex(ind);
        }
        dropDown.addChangeHandler(new ChangeHandler()
        {
            @Override
            public void onChange(ChangeEvent event)
            {
                changeHandler.valueChanged(dropDown.getSelectedValue());
            }
        });
        inputGroup.add(dropDown);
        add(inputGroup);
    }

    private String[] filterAndSort(String[] selectedId)
    {
        if (selectedId == null)
        {
            return new String[0];
        }
        final ArrayList<String> result = new ArrayList<String>(selectedId.length);
        for (String string : selectedId)
        {
            if (string != null)
            {
                result.add(string);
            }
        }
        return result.toArray(new String[result.size()]);
    }

}
