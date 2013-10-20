/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Christopher Kohlhaas                                  |
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
package org.rapla.gui.internal.edit;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.rapla.components.layout.TableLayout;
import org.rapla.components.util.Assert;
import org.rapla.components.util.Tools;
import org.rapla.entities.IllegalAnnotationException;
import org.rapla.entities.UniqueKeyException;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeAnnotations;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.EditComponent;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.toolkit.DialogUI;


/****************************************************************
 * This is the controller-class for the DynamicType-Edit-Panel   *
 ****************************************************************/
class DynamicTypeEditUI extends RaplaGUIComponent
    implements
     EditComponent<DynamicType>
{
    public static String WARNING_SHOWED = DynamicTypeEditUI.class.getName() + "/Warning";
    DynamicType dynamicType;
    JPanel editPanel = new JPanel();
    JPanel annotationPanel = new JPanel();
    JLabel nameLabel = new JLabel();
    MultiLanguageField name;
    JLabel elementKeyLabel = new JLabel();
    TextField elementKey;
    AttributeEdit attributeEdit;

    JLabel annotationLabel = new JLabel();
    JLabel annotationDescription = new JLabel();
    JTextField annotationText = new JTextField();
    JComboBox colorChooser;
    public DynamicTypeEditUI(RaplaContext sm) {
        super(sm);
        colorChooser = new JComboBox(new String[] {getString("color.automated"),getString("color.manual"),getString("color.no")});
        name = new MultiLanguageField(sm,"name");
        elementKey = new TextField(sm,"elementKey");
        attributeEdit = new AttributeEdit(sm);
        nameLabel.setText(getString("dynamictype.name") + ":");
        elementKeyLabel.setText(getString("elementkey") + ":");
        attributeEdit.setEditKeys( true );
        annotationPanel.setVisible( true);
        double[][] sizes = new double[][] {
            {5,TableLayout.PREFERRED,5,TableLayout.FILL,5}
            ,{TableLayout.PREFERRED,5,TableLayout.PREFERRED,5,TableLayout.PREFERRED,5,TableLayout.PREFERRED,5,TableLayout.FILL,5,TableLayout.PREFERRED}
        };
        TableLayout tableLayout = new TableLayout(sizes);
        editPanel.setLayout(tableLayout);
        editPanel.add(nameLabel,"1,2");
        editPanel.add(name.getComponent(),"3,2");
        editPanel.add(elementKeyLabel,"1,4");
        editPanel.add(elementKey.getComponent(),"3,4");
        editPanel.add(attributeEdit.getComponent(),"1,6,3,6");

        // #FIXM Should be replaced by generic solution
        tableLayout.insertRow(7,5);
        tableLayout.insertRow(8,TableLayout.PREFERRED);
        editPanel.add(annotationPanel,"1,8,3,8");
        annotationPanel.setLayout(new TableLayout(new double[][] {
            {TableLayout.PREFERRED,5,TableLayout.FILL}
            ,{TableLayout.PREFERRED,5,TableLayout.PREFERRED,5,TableLayout.PREFERRED}
        }));
        addCopyPaste( annotationText);
        annotationPanel.add(annotationLabel,"0,0");
        annotationPanel.add(annotationText ,"2,0");
        annotationPanel.add(annotationDescription,"2,2");
        annotationPanel.add(new JLabel(getString("color")),"0,4");
        annotationPanel.add(colorChooser,"2,4");
        
        annotationLabel.setText(getString("dynamictype.annotation.nameformat") + ":");
        annotationDescription.setText(getString("dynamictype.annotation.nameformat.description"));
        float newSize = (float) (annotationDescription.getFont().getSize() * 0.8);
        annotationDescription.setFont(annotationDescription.getFont().deriveFont( newSize));
        attributeEdit.addChangeListener( new ChangeListener() {
            public void stateChanged( ChangeEvent e )
            {
                updateAnnotations();
            }

        });
        
        colorChooser.addActionListener(new ActionListener() {
			
    			public void actionPerformed(ActionEvent e) {
    				try {
    					if ( dynamicType.getAttribute("color") != null || colorChooser.getSelectedIndex() != 1)
    					{
    						return;
    					}
    					
    					DialogUI ui = DialogUI.create(getContext(), getMainComponent(), true, getString("color.manual"), getString("attribute_color_dialog"), new String[]{getString("yes"),getString("no")});
						ui.start();
						if (ui.getSelectedIndex() == 0)
						{
							Attribute colorAttribute = getModification().newAttribute(AttributeType.STRING);
							colorAttribute.setKey( "color");
							colorAttribute.getName().setName(getLocale().getLanguage(), getString("color"));
							colorAttribute.setAnnotation(AttributeAnnotations.KEY_EDIT_VIEW, AttributeAnnotations.VALUE_EDIT_VIEW_NO_VIEW);
							dynamicType.addAttribute( colorAttribute);
							attributeEdit.setDynamicType(dynamicType);
						}
						else
						{
							colorChooser.setSelectedIndex(2);
						}
    				} catch (RaplaException ex) {
						showException(ex, getMainComponent());
					}
    				
    			}
    		});
        
        /*
        annotationText.addFocusListener( new FocusAdapter() {

            public void focusLost( FocusEvent e )
            {
                try
                {
                    setAnnotations();
                }
                catch ( RaplaException ex )
                {
                    showException( ex, getComponent());
                }
            }

        });
*/
    }

    public JComponent getComponent() {
        return editPanel;
    }

    public void mapToObjects() throws RaplaException {
        dynamicType.getName().setTo( name.getValue());
        dynamicType.setElementKey(elementKey.getValue());
        attributeEdit.confirmEdits();
        validate();
        setAnnotations();
    }

    private void setAnnotations() throws RaplaException
    {
        try {
            dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT, annotationText.getText().trim());
        } catch (IllegalAnnotationException ex) {
            throw ex;
        }
        String color= null;
        switch (colorChooser.getSelectedIndex())
        {
        	case 0:color = DynamicTypeAnnotations.COLORS_AUTOMATED;break;
        	case 1:color = DynamicTypeAnnotations.COLORS_COLOR_ATTRIBUTE;break;
        	case 2:color = DynamicTypeAnnotations.COLORS_DISABLED;break;
        }
        dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_COLORS, color);

        
    }
    public List<DynamicType> getObjects() {
        List<DynamicType> types = Collections.singletonList(dynamicType);
        return types;
    }

    public void setObjects(List<DynamicType> o) {
        dynamicType =  o.get(0);
        mapFromObjects();
    }
    
    public void mapFromObjects()
    {
        name.setValue( dynamicType.getName());
        elementKey.setValue( dynamicType.getElementKey());
        attributeEdit.setDynamicType(dynamicType);
        updateAnnotations();
    }

    private void updateAnnotations() {
        annotationText.setText( dynamicType.getAnnotation( DynamicTypeAnnotations.KEY_NAME_FORMAT ) );
      
        String annotation = dynamicType.getAnnotation( DynamicTypeAnnotations.KEY_COLORS); 
        if (annotation  == null)
        {
        	annotation =  dynamicType.getAttribute("color") != null ? DynamicTypeAnnotations.COLORS_COLOR_ATTRIBUTE: DynamicTypeAnnotations.COLORS_AUTOMATED;
        }
        if ( annotation.equals( DynamicTypeAnnotations.COLORS_COLOR_ATTRIBUTE))
        {
         	colorChooser.setSelectedIndex(1);
        }
        else if ( annotation.equals(DynamicTypeAnnotations.COLORS_AUTOMATED))
        {
        	colorChooser.setSelectedIndex(0);
        }
        else if ( annotation.equals( DynamicTypeAnnotations.COLORS_DISABLED))
        {
         	colorChooser.setSelectedIndex(2);
        }
    }

    private void validate() throws RaplaException {
        Assert.notNull(dynamicType);
        if ( getName( dynamicType ).length() == 0)
            throw new RaplaException(getString("error.no_name"));

        if (dynamicType.getElementKey().equals("")) {
            throw new RaplaException(getI18n().format("error.no_key",""));
        }
        checkKey(dynamicType.getElementKey());
        Attribute[] attributes = dynamicType.getAttributes();
        for (int i=0;i<attributes.length;i++) {
            String key = attributes[i].getKey();
            if (key == null || key.trim().equals(""))
                throw new RaplaException(getI18n().format("error.no_key","(" + i + ")"));
            checkKey(key);
            for (int j=i+1;j<attributes.length;j++) {
                if ((key.equals(attributes[j].getKey()))) {
                    throw new UniqueKeyException(getI18n().format("error.not_unique",key));
                }
            }
        }
    }

    private void checkKey(String key) throws RaplaException {
        if (key.length() ==0)
            throw new RaplaException(getString("error.no_key"));
        if (!Tools.isKey(key) || key.length()>50) 
        {
            Object[] param = new Object[3];
            param[0] = key;
            param[1] = "'-', '_'";
            param[2] = "'_'";
            throw new RaplaException(getI18n().format("error.invalid_key", param));
        }


    }
}