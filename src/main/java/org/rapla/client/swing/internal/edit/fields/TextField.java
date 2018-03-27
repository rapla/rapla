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
package org.rapla.client.swing.internal.edit.fields;

import org.rapla.RaplaResources;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.toolkit.AWTColorUtil;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class TextField extends AbstractEditField implements ActionListener, FocusListener, KeyListener, MultiEditField, SetGetField<String>
{
    JTextComponent field;
    JComponent colorPanel;
    JScrollPane scrollPane;
    JButton colorChooserBtn;
    JPanel color;
    Object oldValue;
    Color currentColor;

    boolean multipleValues = false; // indicator, shows if multiple different
    // values are shown in this field
    public final static int DEFAULT_LENGTH = 30;

    private TextField(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, IOInterface ioInterface)
    {
        this(facade, i18n, raplaLocale, logger, ioInterface, "", 1, TextField.DEFAULT_LENGTH);
    }

    private TextField(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, IOInterface ioInterface, String fieldName)
    {
        this(facade, i18n, raplaLocale, logger, ioInterface, fieldName, 1, TextField.DEFAULT_LENGTH);
    }

    private TextField(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, IOInterface ioInterface, String fieldName, int rows, int columns)
    {
        super(facade, i18n, raplaLocale, logger);
        setFieldName(fieldName);
        if (rows > 1)
        {
            JTextArea area = new JTextArea();
            field = area;
            scrollPane = new JScrollPane(field, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            area.setColumns(columns);
            area.setRows(rows);
            area.setLineWrap(true);
        }
        else
        {
            field = new JTextField(columns);
        }
        RaplaGUIComponent.addCopyPaste(field, i18n, raplaLocale, ioInterface, logger);
        field.addFocusListener(this);
        field.addKeyListener(this);
        field.setDisabledTextColor(Color.black);
        setValue("");
    }

    public void setColorPanel(boolean show)
    {
        if (!show)
        {
            colorPanel = null;
            return;
        }
        colorPanel = new JPanel();
        color = new JPanel();
        color.setPreferredSize(new Dimension(20, 20));

        color.setBorder(BorderFactory.createEtchedBorder());
        colorPanel.setLayout(new BorderLayout());
        colorPanel.add(field, BorderLayout.CENTER);
        colorPanel.add(color, BorderLayout.WEST);
        colorChooserBtn = new JButton();
        if (field instanceof JTextField)
        {
            ((JTextField) field).setColumns(7);
        }
        else
        {
            ((JTextArea) field).setColumns(7);
        }
        colorPanel.add(colorChooserBtn, BorderLayout.EAST);
        colorChooserBtn.setText(i18n.getString("change"));
        colorChooserBtn.addActionListener(e -> {
            currentColor = JColorChooser.showDialog(colorPanel, "Choose Background Color", currentColor);
            color.setBackground(currentColor);
            if (currentColor != null)
            {
                field.setText(AWTColorUtil.getHexForColor(currentColor));
            }
            fireContentChanged();
        });
    }

    public void setEditable(boolean editable)
    {
        field.setEditable( editable);
    }

    public boolean isEditable()
    {
        return field.isEditable();
    }

    public String getValue()
    {
        return field.getText().trim();
    }

    public void setValue(String string)
    {
        if (string == null)
            string = "";
        field.setText(string);
        oldValue = string;

        if (colorPanel != null)
        {
            try
            {
                currentColor = AWTColorUtil.getColorForHex(string);
            }
            catch (NumberFormatException ex)
            {
                currentColor = null;
            }
            color.setBackground(currentColor);
        }
    }

    public JComponent getComponent()
    {
        if (colorPanel != null)
        {
            return colorPanel;
        }
        if (scrollPane != null)
        {
            return scrollPane;
        }
        else
        {
            return field;
        }
    }

    public void selectAll()
    {
        field.selectAll();
    }

    public void actionPerformed(ActionEvent evt)
    {
        if (field.getText().equals(oldValue))
            return;
        oldValue = field.getText();
        fireContentChanged();
    }

    public void focusLost(FocusEvent evt)
    {

        if (field.getText().equals(oldValue))
            return;
        // checks if entry was executed
        if (field.getText().equals("") && multipleValues)
            // no set place holder for multiple values
            setFieldForMultipleValues();
        else
            // yes: reset flag, because there is just one common entry
            multipleValues = false;
        oldValue = field.getText();
        fireContentChanged();
    }

    public void focusGained(FocusEvent evt)
    {
        Component focusedComponent = evt.getComponent();
        Component parent = focusedComponent.getParent();
        if (parent instanceof JPanel)
        {
            ((JPanel) parent).scrollRectToVisible(focusedComponent.getBounds(null));
        }

        // if the place holder shown for different values, the place holder
        // should be deleted
        if (multipleValues)
        {
            setValue("");
            // set font PLAIN (place holder is shown italic)
            field.setFont(field.getFont().deriveFont(Font.PLAIN));
        }
    }

    public void keyPressed(KeyEvent evt)
    {
    }

    public void keyTyped(KeyEvent evt)
    {
    }

    public void keyReleased(KeyEvent evt)
    {
        if (field.getText().equals(oldValue))
            return;

        // reset flag, because there is just one common entry
        if (multipleValues)
        {
            multipleValues = false;

        }
        oldValue = field.getText();
        fireContentChanged();
    }

    public void setFieldForMultipleValues()
    {
        // set a place holder for multiple different values (italic)
        field.setFont(field.getFont().deriveFont(Font.ITALIC));
        field.setText(TextField.getOutputForMultipleValues());
        multipleValues = true;
    }

    public boolean hasMultipleValues()
    {
        return multipleValues;
    }

    static public String getOutputForMultipleValues()
    {
        // place holder for mulitple different values
        return "<multiple Values>";
    }

    @Singleton
    public static class TextFieldFactory
    {

        private final ClientFacade facade;
        private final RaplaResources i18n;
        private final RaplaLocale raplaLocale;
        private final Logger logger;
        private final IOInterface ioInterface;

        @Inject
        public TextFieldFactory(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, IOInterface ioInterface)
        {
            super();
            this.facade = facade;
            this.i18n = i18n;
            this.raplaLocale = raplaLocale;
            this.logger = logger;
            this.ioInterface = ioInterface;
        }

        public TextField create(String fieldName)
        {
            return new TextField(facade, i18n, raplaLocale, logger, ioInterface, fieldName);
        }

        public TextField create(String fieldName, int rows, int columns)
        {
            return new TextField(facade, i18n, raplaLocale, logger, ioInterface, fieldName, rows, columns);
        }

        public TextField create()
        {
            return new TextField(facade, i18n, raplaLocale, logger, ioInterface);
        }

    }
}
