/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.client.swing.internal.edit;

import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.swing.EditComponent;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.internal.edit.fields.MultiLanguageField;
import org.rapla.client.swing.internal.edit.fields.MultiLanguageField.MultiLanguageFieldFactory;
import org.rapla.client.swing.internal.edit.fields.TextField;
import org.rapla.client.swing.internal.edit.fields.TextField.TextFieldFactory;
import org.rapla.components.calendar.RaplaArrowButton;
import org.rapla.components.layout.TableLayout;
import org.rapla.components.util.Assert;
import org.rapla.entities.Category;
import org.rapla.entities.CategoryAnnotations;
import org.rapla.entities.MultiLanguageName;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;

import javax.inject.Inject;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Extension(provides = EditComponent.class, id = "org.rapla.entities.Category")
public class CategoryEditUI extends RaplaGUIComponent implements EditComponent<Category, JComponent>
{
    JPanel panel = new JPanel();

    private Category category;
    CategoryDetail detailPanel;
    boolean editKeys = true;

    @Inject
    public CategoryEditUI(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger,
            DialogUiFactoryInterface dialogUiFactory, MultiLanguageFieldFactory multiLanguageFieldFactory, TextFieldFactory textFieldFactory)
    {
        super(facade, i18n, raplaLocale, logger);
        detailPanel = new CategoryDetail(facade, i18n, raplaLocale, logger,  dialogUiFactory, multiLanguageFieldFactory, textFieldFactory);
        panel.setPreferredSize(new Dimension(690, 350));
        panel.setLayout(new BorderLayout());
        detailPanel.setEditKeys(editKeys);
        panel.add(detailPanel.getComponent(), BorderLayout.CENTER);
    }

    public JComponent getComponent()
    {
        return panel;
    }

    public void mapToObjects() throws RaplaException
    {
        confirmEdits();
        validate(category);
    }

    public List<Category> getObjects()
    {
        return Collections.singletonList(category);
    }

    public void confirmEdits() throws RaplaException
    {
        detailPanel.mapTo(category);
    }

    private void validate(Category category) throws RaplaException
    {
        DynamicTypeImpl.checkKey(getI18n(), category.getKey());
        Category[] categories = category.getCategories();
        for (int i = 0; i < categories.length; i++)
        {
            final Category category1 = categories[i];
            validate(category1);
        }
    }

    public void setEditKeys(boolean editKeys)
    {
        detailPanel.setEditKeys(editKeys);
        this.editKeys = editKeys;
    }

    @Override
    public void setObjects(List<Category> o) throws RaplaException
    {
        Assert.isTrue(o.size() == 1, "Only one category can be edited once");
        this.category = o.get(0);
        detailPanel.mapFrom(category);
    }

}

class CategoryDetail extends RaplaGUIComponent implements ChangeListener
{

    JPanel mainPanel = new JPanel();
    Category currentCategory;

    JPanel panel = new JPanel();
    JLabel nameLabel = new JLabel();
    JLabel keyLabel = new JLabel();
    JLabel colorLabel = new JLabel();

    MultiLanguageField name;
    TextField key;
    TextField colorTextField;
    JPanel colorPanel = new JPanel();

    RaplaArrowButton addButton = new RaplaArrowButton('>', 25);
    RaplaArrowButton removeButton = new RaplaArrowButton('<', 25);

    public CategoryDetail(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger,
            DialogUiFactoryInterface dialogUiFactory, MultiLanguageFieldFactory multiLanguageFieldFactory, TextFieldFactory textFieldFactory)
    {
        super(facade, i18n, raplaLocale, logger);
        name = multiLanguageFieldFactory.create();
        key = textFieldFactory.create();
        colorTextField = textFieldFactory.create();
        colorTextField.setColorPanel(true);

        double fill = TableLayout.FILL;
        double pre = TableLayout.PREFERRED;
        panel.setLayout(
                new TableLayout(new double[][] { { 5, pre, 5, fill }, // Columns
                { 5, pre, 5, pre, 5, pre, 5 } } // Rows
        ));
        panel.add("1,1,l,f", nameLabel);
        panel.add("3,1,f,f", name.getComponent());
        panel.add("1,3,l,f", keyLabel);
        panel.add("3,3,f,f", key.getComponent());
        panel.add("1,5,l,f", colorLabel);
        panel.add("3,5,f,f", colorPanel);
        colorPanel.setLayout(new BorderLayout());
        colorPanel.add(colorTextField.getComponent(), BorderLayout.CENTER);

        nameLabel.setText(getString("name") + ":");
        keyLabel.setText(getString("key") + ":");
        colorLabel.setText(getString("color") + ":");
        name.addChangeListener(this);
        key.addChangeListener(this);
        colorTextField.addChangeListener(this);

        // Add everything to the MainPanel
        mainPanel.setLayout(new BorderLayout());
        mainPanel.add(panel, BorderLayout.NORTH);

    }

    class CategoryListCellRenderer extends DefaultListCellRenderer
    {
        private static final long serialVersionUID = 1L;
        private boolean filterStyle;

        public CategoryListCellRenderer(boolean filterStyle)
        {
            this.filterStyle = filterStyle;
        }

        public CategoryListCellRenderer()
        {
            this(false);
        }

        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus)
        {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (filterStyle )
                setFont((getFont().deriveFont(Font.PLAIN)));

            if (value != null && value instanceof Category)
            {
                setText(((Category) value).getName(getLocale()));
            }
            return this;
        }
    }

    public void requestFocus()
    {
        name.requestFocus();
    }

    public void setEditKeys(boolean editKeys)
    {
        keyLabel.setVisible(editKeys);
        key.getComponent().setVisible(editKeys);
        colorLabel.setVisible(editKeys);
        colorTextField.getComponent().setVisible(editKeys);
    }

    public JComponent getComponent()
    {
        return mainPanel;
    }

    public void mapFrom(Category category)
    {
        name.setValue((MultiLanguageName) category.getName().clone());
        key.setValue(category.getKey());
        String color = category.getAnnotation(CategoryAnnotations.KEY_NAME_COLOR);
        if (color != null)
        {
            colorTextField.setValue(color);
        }
        else
        {
            colorTextField.setValue(null);
        }
        currentCategory = category;
    }

    public void mapTo(Category category) throws RaplaException
    {
        category.getName().setTo(name.getValue());
        category.setKey(key.getValue());
        String colorValue = colorTextField.getValue().toString().trim();
        if (colorValue.length() > 0)
        {
            category.setAnnotation(CategoryAnnotations.KEY_NAME_COLOR, colorValue);
        }
        else
        {
            category.setAnnotation(CategoryAnnotations.KEY_NAME_COLOR, null);
        }
    }

    public void stateChanged(ChangeEvent e)
    {
        fireContentChanged();
    }

    ArrayList<ChangeListener> listenerList = new ArrayList<>();

    public void addChangeListener(ChangeListener listener)
    {
        listenerList.add(listener);
    }

    public void removeChangeListener(ChangeListener listener)
    {
        listenerList.remove(listener);
    }

    public ChangeListener[] getChangeListeners()
    {
        return listenerList.toArray(new ChangeListener[] {});
    }

    protected void fireContentChanged()
    {
        if (listenerList.size() == 0)
            return;
        ChangeEvent evt = new ChangeEvent(this);
        ChangeListener[] listeners = getChangeListeners();
        for (int i = 0; i < listeners.length; i++)
        {
            listeners[i].stateChanged(evt);
        }
    }
}
