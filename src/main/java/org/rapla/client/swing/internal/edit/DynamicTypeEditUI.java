/*--------------------------------------------------------------------------*
 | Copyright (C) 2013 Christopher Kohlhaas                                  |
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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.extensionpoints.AnnotationEditTypeExtension;
import org.rapla.client.swing.EditComponent;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.internal.edit.annotation.AnnotationEditUI;
import org.rapla.client.swing.internal.edit.fields.MultiLanguageField;
import org.rapla.client.swing.internal.edit.fields.MultiLanguageField.MultiLanguageFieldFactory;
import org.rapla.client.swing.internal.edit.fields.PermissionListField;
import org.rapla.client.swing.internal.edit.fields.PermissionListField.PermissionListFieldFactory;
import org.rapla.client.swing.internal.edit.fields.TextField;
import org.rapla.client.swing.internal.edit.fields.TextField.TextFieldFactory;
import org.rapla.client.swing.toolkit.RaplaButton;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.layout.TableLayout;
import org.rapla.entities.Annotatable;
import org.rapla.entities.IllegalAnnotationException;
import org.rapla.entities.MultiLanguageName;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeAnnotations;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.inject.Extension;


/****************************************************************
 * This is the controller-class for the DynamicType-Edit-Panel   *
 ****************************************************************/
@Extension(provides=EditComponent.class,id= "org.rapla.entities.dynamictype.DynamicType")
public class DynamicTypeEditUI extends RaplaGUIComponent
    implements
     EditComponent<DynamicType,JComponent>
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

    private boolean ignoreListeners = false;
    
    JLabel annotationLabel = new JLabel();
    JLabel annotationDescription = new JLabel();
    
    JTextField annotationText = new JTextField();
    //JTextField annotationTreeText = new JTextField();
    JComboBox colorChooser;
    
    RaplaButton annotationButton = new RaplaButton(RaplaButton.DEFAULT);
    
    //JLabel locationLabel = new JLabel("location");
    //JComboBox locationChooser;
    //JLabel conflictLabel = new JLabel("conflict creation");
	//JComboBox conflictChooser;
    //boolean isResourceType;
    //boolean isEventType;
    AnnotationEditUI annotationEdit;
    DialogInterface dialog;
    PermissionListField permissionListField;
    private final DialogUiFactoryInterface dialogUiFactory;

    @Inject
    public DynamicTypeEditUI(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, final AttributeEdit attributeEdit, Set<AnnotationEditTypeExtension> annotationExtensions, RaplaImages raplaImages, final DialogUiFactoryInterface dialogUiFactory, final PermissionListFieldFactory permissionListFieldFactory, MultiLanguageFieldFactory multiLanguageFieldFactory, TextFieldFactory textFieldFactory, IOInterface ioInterface) throws RaplaInitializationException {
        super(facade, i18n, raplaLocale, logger);
        this.dialogUiFactory = dialogUiFactory;
        annotationEdit = new AnnotationEditUI(facade, i18n, raplaLocale, logger, annotationExtensions);
        {
        	@SuppressWarnings("unchecked")
        	JComboBox jComboBox = new JComboBox(new String[] {getString("color.automated"),getString("color.manual"),getString("color.no")});
        	colorChooser = jComboBox;
        }
      
        name = multiLanguageFieldFactory.create("name");
        elementKey = textFieldFactory.create("elementKey");
        this.attributeEdit = attributeEdit;
        nameLabel.setText(getString("dynamictype.name") + ":");
        elementKeyLabel.setText(getString("elementkey") + ":");
        annotationPanel.setVisible( true);
        double PRE = TableLayout.PREFERRED;
        double[][] sizes = new double[][] {
            {5,PRE,5,TableLayout.FILL,5}
            ,{PRE,5,PRE,5,PRE,5,PRE,5,TableLayout.FILL,5,PRE,5,PRE}
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
        tableLayout.insertRow(8,PRE);
        editPanel.add(annotationPanel,"1,8,3,8");
        annotationPanel.setLayout(new TableLayout(new double[][] {
            {PRE,5,TableLayout.FILL}
            ,{PRE,5,PRE,5,PRE, 5, PRE,5, PRE,5,PRE}
        }));
        addCopyPaste( annotationText, i18n, raplaLocale, ioInterface, logger);
        //addCopyPaste(annotationTreeText);
        annotationPanel.add(annotationLabel,"0,0");
        annotationPanel.add(annotationText ,"2,0");
        annotationPanel.add(annotationDescription,"2,2");
        annotationPanel.add(new JLabel(getString("options") + ":" ),"0,6");
        annotationPanel.add(annotationButton ,"2,6");
        annotationPanel.add(new JLabel(getString("color")+ ":"),"0,4");
        annotationPanel.add(colorChooser,"2,4");
//        annotationPanel.add(locationLabel,"0,8");
//        annotationPanel.add(locationChooser,"2,8");
//        annotationPanel.add(conflictLabel,"0,10");
//        annotationPanel.add(conflictChooser,"2,10");
        annotationLabel.setText(getString("dynamictype.annotation.nameformat") + ":");
        annotationButton.setText(getString("edit"));
        annotationButton.addActionListener(new ActionListener() {
            
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    showAnnotationDialog();
                } catch (RaplaException ex) {
                    dialogUiFactory.showException(ex, new SwingPopupContext(getComponent(), null));
                }
                
            }
        });
     
        try
        {
            this.permissionListField = permissionListFieldFactory.create(getString("permissions"));
        }
        catch (RaplaException e1)
        {
            throw new RaplaInitializationException(e1);
        }
        editPanel.add(this.permissionListField.getComponent(),"1,10,3,10");
        this.permissionListField.setUserSelectVisible( false );
        annotationDescription.setText(getString("dynamictype.annotation.nameformat.description"));
        float newSize = (float) (annotationDescription.getFont().getSize() * 0.8);
        annotationDescription.setFont(annotationDescription.getFont().deriveFont( newSize));
        attributeEdit.addChangeListener( new ChangeListener() {
            public void stateChanged( ChangeEvent e )
            {
                try {
                    updateAnnotations();
                } catch (RaplaException ex) {
                    dialogUiFactory.showException(ex, new SwingPopupContext(getComponent(), null));
                }
            }

        });
        
        colorChooser.addActionListener(new ActionListener() {
			
    			public void actionPerformed(ActionEvent e) {
    			    if ( ignoreListeners )
    			    {
    			        return;
    			    }
    			    try {
    					int selectedIndex = colorChooser.getSelectedIndex();
                        Attribute firstAttributeWithAnnotation = ((DynamicTypeImpl)dynamicType).getFirstAttributeWithAnnotation(AttributeAnnotations.KEY_COLOR);
                        if ( firstAttributeWithAnnotation != null || selectedIndex != 1)
    					{
    						return;
    					}
                        Attribute attribute = dynamicType.getAttribute("color");
                        if ( attribute == null)
                        {
                            attribute = attributeEdit.getSelectedAttribute();
                        }

                        if ( attribute != null)
                        {
                            AttributeType type = attribute.getType();
                            if ( type != AttributeType.STRING  && type != AttributeType.CATEGORY)
                            {
                                dialogUiFactory.showWarning("Only string or category types are allowed for color attribute", new SwingPopupContext(getComponent(), null));
                                colorChooser.setSelectedIndex(2);
                                return;
                            }
                            DialogInterface ui = dialogUiFactory.create(new SwingPopupContext(getMainComponent(), null), true, getString("color.manual"), getString("attribute_color_dialog"), new String[]{getString("yes"),getString("no")});
                            ui.start(true);
                            if (ui.getSelectedIndex() == 0)
                            {
                                attribute.setAnnotation(AttributeAnnotations.KEY_COLOR, "true");
                                attributeEdit.setDynamicType( dynamicType);
                            }
                            else
                            {
                                colorChooser.setSelectedIndex(2);
                            }
                        }
                        else
                        {
                            DialogInterface ui = dialogUiFactory.create(new SwingPopupContext(getMainComponent(), null), true, getString("color.manual"), getString("attribute_color_dialog"), new String[]{getString("yes"),getString("no")});
    						ui.start(true);
    						if (ui.getSelectedIndex() == 0)
    						{
    							createNewColorAttribute();
    						}
    						else
    						{
    							colorChooser.setSelectedIndex(2);
    						}
                        }
    				} catch (RaplaException ex) {
    				    dialogUiFactory.showException(ex, new SwingPopupContext(getMainComponent(), null));
					}
    				
    			}

                private void createNewColorAttribute() throws RaplaException
                {
                    Attribute colorAttribute = getFacade().newAttribute(AttributeType.STRING);
                    colorAttribute.setKey( "color");
                    colorAttribute.setAnnotation(AttributeAnnotations.KEY_COLOR, "true");
                    colorAttribute.getName().setName(getLocale().getLanguage(), getString("color"));
                    colorAttribute.setAnnotation(AttributeAnnotations.KEY_EDIT_VIEW, AttributeAnnotations.VALUE_EDIT_VIEW_NO_VIEW);
                    dynamicType.addAttribute( colorAttribute);
                    attributeEdit.setDynamicType(dynamicType);
                }
    		});
        
    }

    public JComponent getComponent() {
        return editPanel;
    }

    public void mapToObjects() throws RaplaException {
        MultiLanguageName newName = name.getValue();
		dynamicType.getName().setTo( newName);
        dynamicType.setKey(elementKey.getValue());
        attributeEdit.confirmEdits();
        permissionListField.mapTo( Collections.singletonList( dynamicType));
        DynamicTypeImpl.validate(dynamicType, getI18n());
        setAnnotations();
    }

    private void setAnnotations() throws RaplaException
    {
        try {
            dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT, annotationText.getText().trim());
      //      String planningText = annotationTreeText.getText().trim();
        //    dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT_PLANNING, planningText.length() > 0 ? planningText : null);
        } catch (IllegalAnnotationException ex) {
            throw ex;
        }
        String color= null;
        switch (colorChooser.getSelectedIndex())
        {
        	case 0:color = DynamicTypeAnnotations.VALUE_COLORS_AUTOMATED;break;
        	case 1:color = DynamicTypeAnnotations.VALUE_COLORS_COLOR_ATTRIBUTE;break;
        	case 2:color = DynamicTypeAnnotations.VALUE_COLORS_DISABLED;break;
        }
        dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_COLORS, color);
//        if ( isResourceType)
//        {
//        	String location = null;
//	        switch (locationChooser.getSelectedIndex())
//	        {
//	        	case 0:location = "true";break;
//	        	case 1:location = "false";break;
//	        }
//	        if ( location == null || location.equals( "false"))
//	        {
//	        	dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_LOCATION, null);
//	        }
//	        else
//	        {
//	        	dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_LOCATION, location);
//	        }
//        }
//        if ( isEventType)
//        {
//	        String conflicts = null;
//	        switch (conflictChooser.getSelectedIndex())
//	        {
//	        	case 0:conflicts = DynamicTypeAnnotations.VALUE_CONFLICTS_ALWAYS;break;
//	        	case 1:conflicts = DynamicTypeAnnotations.VALUE_CONFLICTS_NONE;break;
//	        	case 2:conflicts = DynamicTypeAnnotations.VALUE_CONFLICTS_WITH_OTHER_TYPES;break;
//	        }
//            if ( conflicts == null || conflicts.equals( DynamicTypeAnnotations.VALUE_CONFLICTS_ALWAYS))
//	        {
//	        	dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_CONFLICTS, null);
//	        }
//	        else
//	        {
//	        	dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_CONFLICTS, conflicts);
//	        }
//	
//	        
//        }
    }
    
    private void showAnnotationDialog() throws RaplaException
    {
        boolean modal = false;
        if (dialog != null)
        {
            dialog.close();
        }
        dialog = dialogUiFactory.create(
                new SwingPopupContext(getComponent(), null)
                ,modal
                ,annotationEdit.getComponent()
                ,new String[] { getString("close")});

        dialog.getAction(0).setRunnable( new Runnable() {
            private static final long serialVersionUID = 1L;
            public void run() {
                List<Annotatable> asList = Arrays.asList((Annotatable)dynamicType);
                try {
                    annotationEdit.mapTo(asList);
                } catch (Exception e1) {
                    getLogger().error(e1.getMessage(), e1);
                    dialogUiFactory.showException( e1, new SwingPopupContext(getMainComponent(), null));
                }
                dialog.close();
            }
        });
        dialog.setTitle(getString("select"));
        dialog.start(true);
    }

    
    public List<DynamicType> getObjects() {
        List<DynamicType> types = Collections.singletonList(dynamicType);
        return types;
    }

    public void setObjects(List<DynamicType> o) throws RaplaException {
        dynamicType =  o.get(0);
        mapFromObjects();
    }
    
    public void mapFromObjects() throws RaplaException
    {
        name.setValue( dynamicType.getName());
        elementKey.setValue( dynamicType.getKey());
        attributeEdit.setDynamicType(dynamicType);
        String annotation = dynamicType.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
        if ( annotation != null && annotation.equals( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION))
        {
            permissionListField.setPermissionLevels(Permission.DENIED,Permission.READ_TYPE, Permission.CREATE,Permission.DENIED, Permission.READ,Permission.EDIT, Permission.ADMIN);
            permissionListField.setDefaultAccessLevel( Permission.READ );
        }

        permissionListField.mapFrom( Collections.singletonList(dynamicType));
//        String classificationType = dynamicType.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
//		isEventType = classificationType != null && classificationType.equals( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
//		isResourceType = classificationType != null && classificationType.equals( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE);
//		conflictLabel.setVisible( isEventType);
//		conflictChooser.setVisible( isEventType);
//		locationLabel.setVisible( isResourceType);
//		locationChooser.setVisible( isResourceType);

        updateAnnotations();
    }

    private void updateAnnotations() throws RaplaException {
        
        
        annotationText.setText( dynamicType.getAnnotation( DynamicTypeAnnotations.KEY_NAME_FORMAT ) );
        //annotationTreeText.setText( dynamicType.getAnnotation( DynamicTypeAnnotations.KEY_NAME_FORMAT_PLANNING,"" ) );
        List<Annotatable> asList = Arrays.asList((Annotatable)dynamicType);
        annotationEdit.setObjects(asList);
        try
        {
            ignoreListeners = true;
            String annotation = dynamicType.getAnnotation( DynamicTypeAnnotations.KEY_COLORS); 
	        if (annotation  == null)
	        {
	        	annotation =  dynamicType.getAttribute("color") != null ? DynamicTypeAnnotations.VALUE_COLORS_COLOR_ATTRIBUTE: DynamicTypeAnnotations.VALUE_COLORS_AUTOMATED;
	        }
	        if ( annotation.equals(DynamicTypeAnnotations.VALUE_COLORS_AUTOMATED))
	        {
	        	colorChooser.setSelectedIndex(0);
	        }
	        else if ( annotation.equals( DynamicTypeAnnotations.VALUE_COLORS_COLOR_ATTRIBUTE))
	        {
	         	colorChooser.setSelectedIndex(1);
	        }
	        else if ( annotation.equals( DynamicTypeAnnotations.VALUE_COLORS_DISABLED))
	        {
	         	colorChooser.setSelectedIndex(2);
	        }
        }
        finally
        {
            ignoreListeners = false;
        }
//        if ( isEventType)
//        {
//	        String annotation = dynamicType.getAnnotation( DynamicTypeAnnotations.KEY_CONFLICTS); 
//	        if (annotation  == null)
//	        {
//	        	annotation =  DynamicTypeAnnotations.VALUE_CONFLICTS_ALWAYS;
//	        }
//	        if ( annotation.equals( DynamicTypeAnnotations.VALUE_CONFLICTS_ALWAYS))
//	        {
//	         	conflictChooser.setSelectedIndex(0);
//	        }
//	        else if ( annotation.equals(DynamicTypeAnnotations.VALUE_CONFLICTS_NONE))
//	        {
//	        	conflictChooser.setSelectedIndex(1);
//	        }
//	        else if ( annotation.equals( DynamicTypeAnnotations.VALUE_CONFLICTS_WITH_OTHER_TYPES))
//	        {
//	        	conflictChooser.setSelectedIndex(2);
//	        }
//        }
//        if ( isResourceType)
//        {
//	        String annotation = dynamicType.getAnnotation( DynamicTypeAnnotations.KEY_LOCATION); 
//	        if (annotation  == null)
//	        {
//	        	annotation =  "false";
//	        }
//	        if ( annotation.equals( "true"))
//	        {
//	        	locationChooser.setSelectedIndex(0);
//	        }
//	        else
//	        {
//	        	locationChooser.setSelectedIndex(1);
//	        }
//        }
    }
}