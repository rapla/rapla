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
package org.rapla.gui.internal.edit;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.rapla.components.calendar.DateChangeEvent;
import org.rapla.components.calendar.DateChangeListener;
import org.rapla.components.calendar.RaplaCalendar;
import org.rapla.components.calendar.RaplaNumber;
import org.rapla.components.layout.TableLayout;
import org.rapla.entities.Annotatable;
import org.rapla.entities.Category;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeAnnotations;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.framework.Container;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.AnnotationEditExtension;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.internal.edit.RaplaListEdit.NameProvider;
import org.rapla.gui.internal.edit.annotation.AnnotationEditUI;
import org.rapla.gui.internal.edit.fields.AbstractEditField;
import org.rapla.gui.internal.edit.fields.BooleanField;
import org.rapla.gui.internal.edit.fields.CategorySelectField;
import org.rapla.gui.internal.edit.fields.ListField;
import org.rapla.gui.internal.edit.fields.MultiLanguageField;
import org.rapla.gui.internal.edit.fields.TextField;
import org.rapla.gui.toolkit.DialogUI;
import org.rapla.gui.toolkit.EmptyLineBorder;
import org.rapla.gui.toolkit.RaplaButton;
import org.rapla.gui.toolkit.RaplaWidget;

public class AttributeEdit extends RaplaGUIComponent
    implements
    RaplaWidget
{
    RaplaListEdit<Attribute> listEdit;
    DynamicType dt;
    DefaultConstraints constraintPanel;
    ArrayList<ChangeListener> listenerList = new ArrayList<ChangeListener>();

    Listener listener = new Listener();
    DefaultListModel model = new DefaultListModel();
    
    public AttributeEdit(RaplaContext context) throws RaplaException {
        super( context);
        constraintPanel = new DefaultConstraints(context);
        listEdit = new RaplaListEdit<Attribute>( getI18n(), constraintPanel.getComponent(), listener );
        listEdit.setListDimension( new Dimension( 200,220 ) );

        constraintPanel.addChangeListener( listener );
        listEdit.setNameProvider( new NameProvider<Attribute>()
                {
                    @Override
                    public String getName(Attribute a) {
                        String value = a.getName(getRaplaLocale().getLocale());
                        value = "{" + a.getKey() + "} " + value;
                        int index = listEdit.indexOf( a);
                        if ( index >= 0)
                        {
                            value = (index + 1) +") " + value;
                        }
                        return value;
                    }
                }
                );
        listEdit.getComponent().setBorder( BorderFactory.createTitledBorder( new EmptyLineBorder(),getString("attributes")) );
    }


    public RaplaWidget getConstraintPanel() {
        return constraintPanel;
    }
    
    public Attribute getSelectedAttribute()
    {
        return listEdit.getSelectedValue();
    }
    
    public void selectAttribute( Attribute attribute)
    {
        
        boolean shouldScroll = true;
        listEdit.getList().setSelectedValue( attribute, shouldScroll);
    }
   

    class Listener implements ActionListener,ChangeListener {
        public void actionPerformed(ActionEvent evt) {
            int index = getSelectedIndex();
            try {
                if (evt.getActionCommand().equals("remove")) {
                    removeAttribute();
                } else if (evt.getActionCommand().equals("new")) {
                    createAttribute();
                } else if (evt.getActionCommand().equals("edit")) {
                    Attribute attribute = (Attribute) listEdit.getList().getSelectedValue();
                    constraintPanel.mapFrom( attribute );
                } else if (evt.getActionCommand().equals("moveUp")) {
                    dt.exchangeAttributes(index, index -1);
                    updateModel(null);
                } else if (evt.getActionCommand().equals("moveDown")) {
                    dt.exchangeAttributes(index, index + 1);
                    updateModel(null);
                }

            } catch (RaplaException ex) {
                showException(ex, getComponent());
            }
        }
        public void stateChanged(ChangeEvent e) {
            try {
                confirmEdits();
                fireContentChanged();
            } catch (RaplaException ex) {
                showException(ex, getComponent());
            }
        }
    }

    public JComponent getComponent() {
        return listEdit.getComponent();
    }

    public int getSelectedIndex() {
        return listEdit.getList().getSelectedIndex();
    }

    public void setDynamicType(DynamicType dt)  {
        this.dt = dt;
        updateModel(null);
    }

    @SuppressWarnings("unchecked")
	private void updateModel(Attribute newSelectedItem) {
        Attribute selectedItem = newSelectedItem != null ? newSelectedItem : listEdit.getSelectedValue();
        model.clear();
        Attribute[] attributes = dt.getAttributes();
        for (int i = 0; i < attributes.length; i++ ) {
            model.addElement( attributes[i] );
        }
        listEdit.getList().setModel(model);
        if ( listEdit.getSelectedValue() != selectedItem )
            listEdit.getList().setSelectedValue(selectedItem, true );
    }

    @SuppressWarnings("unchecked")
	public void confirmEdits() throws RaplaException {
        if ( getSelectedIndex() < 0 )
            return;
        Attribute attribute =  listEdit.getSelectedValue();
        constraintPanel.mapTo (attribute );
        model.set( model.indexOf( attribute ), attribute );
    }

    private String createNewKey() {
        Attribute[] atts = dt.getAttributes();
        int max = 1;
        for (int i=0;i<atts.length;i++) {
            String key = atts[i].getKey();
            if (key.length()>1
                && key.charAt(0) =='a'
                && Character.isDigit(key.charAt(1))
                )
                {
                    try {
                        int value = Integer.valueOf(key.substring(1)).intValue();
                        if (value >= max)
                            max = value + 1;
                    } catch (NumberFormatException ex) {
                    }
                }
        }
        return "a" + (max);
    }

    void removeAttribute()  {
    	List<Attribute> toRemove = new ArrayList<Attribute>();
    	
    	for ( int index:listEdit.getList().getSelectedIndices())
    	{
    		Attribute att = dt.getAttributes() [index];
    		toRemove.add( att);
    	}
    	for (Attribute att:toRemove)
    	{
    		dt.removeAttribute(att);
    	}
        updateModel(null);
    }

    void createAttribute() throws RaplaException {
        confirmEdits();
        AttributeType type = AttributeType.STRING;
        Attribute att =  getModification().newAttribute(type);
        String language = getRaplaLocale().getLocale().getLanguage();
		att.getName().setName(language, getString("attribute"));
        att.setKey(createNewKey());
        dt.addAttribute(att);
        updateModel( att);
//        int index = dt.getAttributes().length -1;
//		listEdit.getList().setSelectedIndex( index );
		constraintPanel.name.selectAll();
		constraintPanel.name.requestFocus();
		
    }

    public void addChangeListener(ChangeListener listener) {
        listenerList.add(listener);
    }

    public void removeChangeListener(ChangeListener listener) {
        listenerList.remove(listener);
    }

    public ChangeListener[] getChangeListeners() {
        return listenerList.toArray(new ChangeListener[]{});
    }

    protected void fireContentChanged() {
        if (listenerList.size() == 0)
            return;
        ChangeEvent evt = new ChangeEvent(this);
        ChangeListener[] listeners = getChangeListeners();
        for (int i = 0;i<listeners.length; i++) {
            listeners[i].stateChanged(evt);
        }
    }

}

class DefaultConstraints extends AbstractEditField
    implements
        ActionListener
        ,ChangeListener
{
    JPanel panel = new JPanel();
    JLabel nameLabel = new JLabel();
    JLabel keyLabel = new JLabel();
    JLabel typeLabel = new JLabel();
    JLabel categoryLabel = new JLabel();
    JLabel dynamicTypeLabel = new JLabel();
    JLabel defaultLabel = new JLabel();
    JLabel multiSelectLabel = new JLabel();
    JLabel tabLabel = new JLabel();
    JLabel specialkeyLabel = new JLabel(); // BJO
    AttributeType types[] = {
        AttributeType.BOOLEAN
        ,AttributeType.STRING
        ,AttributeType.INT
        ,AttributeType.CATEGORY
        ,AttributeType.ALLOCATABLE 
        ,AttributeType.DATE
    };

    String tabs[] = {
            AttributeAnnotations.VALUE_EDIT_VIEW_MAIN
            ,AttributeAnnotations.VALUE_EDIT_VIEW_ADDITIONAL
            ,AttributeAnnotations.VALUE_EDIT_VIEW_NO_VIEW
    };

    boolean mapping = false;
    MultiLanguageField name ;
    TextField key;
    JComboBox classSelect = new JComboBox();
    ListField<DynamicType> dynamicTypeSelect;
    
    CategorySelectField categorySelect;
    CategorySelectField defaultSelectCategory;
    TextField defaultSelectText;
    BooleanField defaultSelectBoolean;
    BooleanField multiSelect;
    RaplaNumber defaultSelectNumber = new RaplaNumber(new Long(0),null,null, false);
    RaplaCalendar defaultSelectDate ;
    RaplaButton annotationButton = new RaplaButton(RaplaButton.DEFAULT);
    JComboBox tabSelect = new JComboBox();
    DialogUI dialog;
    boolean emailPossible = false;
    Category rootCategory;
    AnnotationEditUI annotationEdit;
    Attribute attribute;
    
    DefaultConstraints(RaplaContext context) throws RaplaException{
        super( context );
        Collection<AnnotationEditExtension> annotationExtensions = context.lookup(Container.class).lookupServicesFor(AnnotationEditExtension.ATTRIBUTE_ANNOTATION_EDIT);
        annotationEdit = new AnnotationEditUI(context, annotationExtensions);
        key = new TextField(context);
        name = new MultiLanguageField(context);
        Collection<DynamicType> typeList = new ArrayList<DynamicType>(Arrays.asList(getQuery().getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE)));
        typeList.addAll(Arrays.asList(getQuery().getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON)));
        dynamicTypeSelect = new ListField<DynamicType>(context,true );
        dynamicTypeSelect.setVector( typeList );
        rootCategory = getQuery().getSuperCategory();

        categorySelect = new CategorySelectField(context,rootCategory);
        categorySelect.setUseNull(false);
        defaultSelectCategory = new CategorySelectField(context,rootCategory);
        defaultSelectText = new TextField(context);
        addCopyPaste( defaultSelectNumber.getNumberField());
        //addCopyPaste( expectedRows.getNumberField());
        //addCopyPaste( expectedColumns.getNumberField());

        defaultSelectBoolean = new BooleanField(context);
        defaultSelectDate = createRaplaCalendar();
        defaultSelectDate.setNullValuePossible( true);
        defaultSelectDate.setDate( null);
        multiSelect = new BooleanField(context);
        double fill = TableLayout.FILL;
        double pre = TableLayout.PREFERRED;
        panel.setLayout( new TableLayout( new double[][]
            {{5, pre, 5, fill },  // Columns
             {5, pre ,5, pre, 5, pre, 5, pre, 5, pre, 5, pre, 5,pre, 5, pre, 5}} // Rows
                                          ));
        panel.add("1,1,l,f", nameLabel);
        panel.add("3,1,f,f", name.getComponent() );
        panel.add("1,3,l,f", keyLabel);
        panel.add("3,3,f,f", key.getComponent() );
        panel.add("1,5,l,f", typeLabel);
        panel.add("3,5,l,f", classSelect);
        
        // constraints
        panel.add("1,7,l,t", categoryLabel);
        panel.add("3,7,l,t", categorySelect.getComponent());
        panel.add("1,7,l,t", dynamicTypeLabel);
        panel.add("3,7,l,t", dynamicTypeSelect.getComponent());
        panel.add("1,9,l,t", defaultLabel);
        panel.add("3,9,l,t", defaultSelectCategory.getComponent());
        panel.add("3,9,l,t", defaultSelectText.getComponent());
        panel.add("3,9,l,t", defaultSelectBoolean.getComponent());
        panel.add("3,9,l,t", defaultSelectDate);
        panel.add("3,9,l,t", defaultSelectNumber);
        panel.add("1,11,l,t", multiSelectLabel);
        panel.add("3,11,l,t", multiSelect.getComponent());
        panel.add("1,13,l,t", tabLabel);
        panel.add("3,13,l,t", tabSelect);
        panel.add("1,15,l,t", specialkeyLabel); // BJO
        panel.add("3,15,l,t", annotationButton);
        annotationButton.setText(getString("edit"));
        annotationButton.addActionListener(new ActionListener() {
            
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    showAnnotationDialog();
                } catch (RaplaException ex) {
                    showException(ex, getComponent());
                }
                
            }
        });
      
        setModel();

        nameLabel.setText(getString("name") + ":");
        keyLabel.setText(getString("key") +" *" + ":");
        typeLabel.setText(getString("type") + ":");
        categoryLabel.setText(getString("root") + ":");
        dynamicTypeLabel.setText(getString("root") + ":");
        tabLabel.setText(getString("edit-view") + ":");
        multiSelectLabel.setText("Multiselect:");
        defaultLabel.setText(getString("default") + ":");
        specialkeyLabel.setText(getString("options") + ":");
        categorySelect.addChangeListener ( this );
        categorySelect.addChangeListener( new ChangeListener() {
            
            public void stateChanged(ChangeEvent e) 
            {
                final Category rootCategory = categorySelect.getValue();
                defaultSelectCategory.setRootCategory( rootCategory );
                defaultSelectCategory.setValue( null);
                defaultSelectCategory.getComponent().setEnabled( rootCategory != null);
            }
        }
        
        );
        name.addChangeListener ( this );
        key.addChangeListener ( this );
        classSelect.addActionListener ( this );
        tabSelect.addActionListener( this);
        multiSelect.addChangeListener( this );
        defaultSelectCategory.addChangeListener( this );
        defaultSelectText.addChangeListener( this );
        defaultSelectBoolean.addChangeListener( this );
        defaultSelectNumber.addChangeListener( this );
        defaultSelectDate.addDateChangeListener( new DateChangeListener() {
            
            public void dateChanged(DateChangeEvent evt) 
            {
                stateChanged(null);
            }
        });
    }

	@SuppressWarnings("unchecked")
	private void setModel() {
		DefaultComboBoxModel model = new DefaultComboBoxModel();
        for ( int i = 0; i < types.length; i++ ) {
            model.addElement(getString("type." + types[i]));
        }
        classSelect.setModel( model );

        model = new DefaultComboBoxModel();
        for ( int i = 0; i < tabs.length; i++ ) {
            model.addElement(getString(tabs[i]));
        }
        tabSelect.setModel( model );
	}

    public JComponent getComponent() {
        return panel;
    }

    private void clearValues() {
    	 categorySelect.setValue(null);
         defaultSelectCategory.setValue( null);
         defaultSelectText.setValue("");
         defaultSelectBoolean.setValue( null);
         defaultSelectNumber.setNumber(null);
         defaultSelectDate.setDate(null);
         multiSelect.setValue( Boolean.FALSE);
	}

    public void mapFrom(Attribute attribute) throws RaplaException  {
    	clearValues();
        try {
            mapping = true;
            this.attribute = attribute;
            clearValues();
            String classificationType = attribute.getDynamicType().getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
			emailPossible = classificationType != null && (classificationType.equals( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON) || classificationType.equals( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE));
            name.setValue( attribute.getName());
            key.setValue( attribute.getKey());
            final AttributeType attributeType = attribute.getType();
            classSelect.setSelectedItem(getString("type." + attributeType));
            if (attributeType.equals(AttributeType.CATEGORY)) {
                final Category rootCategory = (Category)attribute.getConstraint(ConstraintIds.KEY_ROOT_CATEGORY);
                categorySelect.setValue( rootCategory );
                defaultSelectCategory.setRootCategory( rootCategory);
                defaultSelectCategory.setValue( (Category)attribute.convertValue(attribute.defaultValue()));
                defaultSelectCategory.getComponent().setEnabled( rootCategory != null);
            }
            else if (attributeType.equals(AttributeType.ALLOCATABLE)) {
                final DynamicType rootCategory = (DynamicType)attribute.getConstraint(ConstraintIds.KEY_DYNAMIC_TYPE);
                dynamicTypeSelect.setValue( rootCategory );
            }
            else if (attributeType.equals(AttributeType.STRING)) 
            {
                defaultSelectText.setValue( (String)attribute.defaultValue());
            }
            else if (attributeType.equals(AttributeType.BOOLEAN)) 
            {
                defaultSelectBoolean.setValue( (Boolean)attribute.defaultValue());
            }
            else if (attributeType.equals(AttributeType.INT)) 
            {
                defaultSelectNumber.setNumber( (Number)attribute.defaultValue());
            }
            else if (attributeType.equals(AttributeType.DATE)) 
            {
                defaultSelectDate.setDate( (Date)attribute.defaultValue());
            }
            
            if (attributeType.equals(AttributeType.CATEGORY) || attributeType.equals(AttributeType.ALLOCATABLE)) {
            	Boolean multiSelectValue = (Boolean) attribute.getConstraint(ConstraintIds.KEY_MULTI_SELECT) ;
            	multiSelect.setValue( multiSelectValue != null ? multiSelectValue: Boolean.FALSE );
            }
            String selectedTab = attribute.getAnnotation(AttributeAnnotations.KEY_EDIT_VIEW, AttributeAnnotations.VALUE_EDIT_VIEW_MAIN);
            tabSelect.setSelectedItem(getString(selectedTab));
            update();
        } finally {
            mapping = false;
        }
    }

    public void mapTo(Attribute attribute) throws RaplaException  {
        attribute.getName().setTo( name.getValue());
        attribute.setKey( key.getValue());
        AttributeType type = types[classSelect.getSelectedIndex()];
        attribute.setType( type );
        if ( type.equals(AttributeType.CATEGORY)) {
            Object defaultValue = defaultSelectCategory.getValue();
            Object rootCategory = categorySelect.getValue();
            if ( rootCategory == null)
            {
                rootCategory = this.rootCategory;
                defaultValue = null;
            }
            attribute.setConstraint(ConstraintIds.KEY_ROOT_CATEGORY, rootCategory );
            attribute.setDefaultValue( defaultValue);
        } else {
            attribute.setConstraint(ConstraintIds.KEY_ROOT_CATEGORY, null);
        }
        
        if ( type.equals(AttributeType.ALLOCATABLE)) {
            Object rootType = dynamicTypeSelect.getValue();
//            if ( rootType == null)
//            {
//                rootType = this.rootCategory;
//            }
            attribute.setConstraint(ConstraintIds.KEY_DYNAMIC_TYPE, rootType );
	        attribute.setDefaultValue( null);
        } else {
            attribute.setConstraint(ConstraintIds.KEY_DYNAMIC_TYPE, null);
        }
        
        if ( type.equals(AttributeType.ALLOCATABLE) || type.equals(AttributeType.CATEGORY))
        {
            Boolean value = multiSelect.getValue();
            attribute.setConstraint(ConstraintIds.KEY_MULTI_SELECT, value);
        }
        else
        {
        	attribute.setConstraint(ConstraintIds.KEY_MULTI_SELECT, null);
        }
        
        if ( type.equals(AttributeType.BOOLEAN)) {
            final Object defaultValue = defaultSelectBoolean.getValue();
            attribute.setDefaultValue( defaultValue);
        } 
        
        if ( type.equals(AttributeType.INT)) {
            final Object defaultValue = defaultSelectNumber.getNumber();
            attribute.setDefaultValue( defaultValue);
        }
        
        if ( type.equals(AttributeType.DATE)) {
            final Object defaultValue = defaultSelectDate.getDate();
            attribute.setDefaultValue( defaultValue);
        }
        if ( type.equals(AttributeType.STRING)) {
            final Object defaultValue = defaultSelectText.getValue();
            attribute.setDefaultValue( defaultValue);
        }
        List<Annotatable> asList = Arrays.asList((Annotatable)attribute);
        annotationEdit.mapTo(asList);
        String selectedTab = tabs[tabSelect.getSelectedIndex()];
        if ( selectedTab != null && !selectedTab.equals(AttributeAnnotations.VALUE_EDIT_VIEW_MAIN)) {
            attribute.setAnnotation(AttributeAnnotations.KEY_EDIT_VIEW,  selectedTab);
        } else {
            attribute.setAnnotation(AttributeAnnotations.KEY_EDIT_VIEW,  null);
        }
    }

    private void update() throws RaplaException {
        AttributeType type = types[classSelect.getSelectedIndex()];
        List<Annotatable> asList = Arrays.asList((Annotatable)attribute);
        annotationEdit.setObjects( asList);
        final boolean categoryVisible = type.equals(AttributeType.CATEGORY);
        final boolean allocatableVisible = type.equals(AttributeType.ALLOCATABLE);
        final boolean textVisible = type.equals(AttributeType.STRING);
        final boolean booleanVisible = type.equals(AttributeType.BOOLEAN);
        final boolean numberVisible = type.equals(AttributeType.INT);
        final boolean dateVisible  = type.equals(AttributeType.DATE);
        categoryLabel.setVisible( categoryVisible );
        categorySelect.getComponent().setVisible( categoryVisible );
        dynamicTypeLabel.setVisible( allocatableVisible);
        dynamicTypeSelect.getComponent().setVisible( allocatableVisible);
        defaultLabel.setVisible( !allocatableVisible);
        defaultSelectCategory.getComponent().setVisible( categoryVisible);
        defaultSelectText.getComponent().setVisible( textVisible);
        defaultSelectBoolean.getComponent().setVisible( booleanVisible);
        defaultSelectNumber.setVisible( numberVisible);
        defaultSelectDate.setVisible( dateVisible);
        multiSelectLabel.setVisible( categoryVisible || allocatableVisible);
        multiSelect.getComponent().setVisible( categoryVisible || allocatableVisible);
    }
    
    private void showAnnotationDialog() throws RaplaException
    {
        RaplaContext context = getContext();
        boolean modal = false;
        if (dialog != null)
        {
            dialog.close();
        }
        dialog = DialogUI.create(context
                ,getComponent()
                ,modal
                ,annotationEdit.getComponent()
                ,new String[] { getString("close")});

        dialog.getButton(0).setAction( new AbstractAction() {
            private static final long serialVersionUID = 1L;
            public void actionPerformed(ActionEvent e) {
                fireContentChanged();
                dialog.close();
            }
        });
        dialog.setTitle(getString("select"));
        dialog.start();
    }
    
    public void actionPerformed(ActionEvent evt) {
        if (mapping)
            return;
        if ( evt.getSource() == classSelect) {
        	clearValues();
            AttributeType newType = types[classSelect.getSelectedIndex()];
            if (newType.equals(AttributeType.CATEGORY)) {
                categorySelect.setValue( rootCategory );
            }
        }
        fireContentChanged();
        try {
            update();
        } catch (RaplaException ex) {
            showException(ex, getComponent());
        }
    }

	public void stateChanged(ChangeEvent e) {
        if (mapping)
            return;
        fireContentChanged();
    }

  
}

