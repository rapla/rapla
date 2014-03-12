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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.rapla.components.calendar.DateChangeEvent;
import org.rapla.components.calendar.DateChangeListener;
import org.rapla.components.calendar.RaplaCalendar;
import org.rapla.components.calendar.RaplaNumber;
import org.rapla.components.layout.TableLayout;
import org.rapla.entities.Category;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeAnnotations;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.toolkit.EmptyLineBorder;
import org.rapla.gui.toolkit.RaplaWidget;

/**
 *  @author Christopher Kohlhaas
 */
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
    boolean editKeys;

    public AttributeEdit(RaplaContext sm) throws RaplaException {
        super( sm);
        constraintPanel = new DefaultConstraints(sm);
        listEdit = new RaplaListEdit<Attribute>( getI18n(), constraintPanel.getComponent(), listener );
        listEdit.setListDimension( new Dimension( 200,220 ) );

        constraintPanel.addChangeListener( listener );

        listEdit.getComponent().setBorder( BorderFactory.createTitledBorder( new EmptyLineBorder(),getString("attributes")) );
        setRender();
        constraintPanel.setEditKeys( false );
    }

	@SuppressWarnings("unchecked")
	private void setRender() {
		listEdit.getList().setCellRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

                public Component getListCellRendererComponent(JList list,
                                                              Object value,
                                                              int index,
                                                              boolean isSelected,
                                                              boolean cellHasFocus) {
                    Attribute a = (Attribute) value;
                    value = a.getName(getRaplaLocale().getLocale());
                    if (editKeys) {
                        value = "{" + a.getKey() + "} " + value;
                    }
                    value = (index + 1) +") " + value;
                    return super.getListCellRendererComponent(list,value,index,isSelected,cellHasFocus);
               }
            });
	}

    public RaplaWidget getConstraintPanel() {
        return constraintPanel;
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
                    updateModel();
                } else if (evt.getActionCommand().equals("moveDown")) {
                    dt.exchangeAttributes(index, index + 1);
                    updateModel();
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
        updateModel();
    }

    @SuppressWarnings("unchecked")
	private void updateModel() {
        Attribute selectedItem = listEdit.getSelectedValue();
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

    public void setEditKeys(boolean editKeys) {
        constraintPanel.setEditKeys(editKeys);
        this.editKeys = editKeys;
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
        updateModel();
    }

    void createAttribute() throws RaplaException {
        confirmEdits();
        AttributeType type = AttributeType.STRING;
        Attribute att =  getModification().newAttribute(type);
        String language = getRaplaLocale().getLocale().getLanguage();
		att.getName().setName(language, getString("attribute"));
        att.setKey(createNewKey());
        dt.addAttribute(att);
        updateModel();
        int index = dt.getAttributes().length -1;
		listEdit.getList().setSelectedIndex( index );
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
    JLabel expectedColumnsLabel = new JLabel();
    JLabel expectedRowsLabel = new JLabel();
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
    static final Long DEFAULT_ROWS = new Long(1);
    static final Long DEFAULT_COLUMNS = new Long(1);
    
    RaplaNumber expectedRows = new RaplaNumber(DEFAULT_ROWS,new Long(1),null, false);
    RaplaNumber expectedColumns = new RaplaNumber(DEFAULT_COLUMNS,new Long(1),null, false);
    JComboBox tabSelect = new JComboBox();

    Category rootCategory;

    DefaultConstraints(RaplaContext sm) throws RaplaException{
        super( sm );
        key = new TextField(sm,"key");
        name = new MultiLanguageField(sm,"name");
        Collection<DynamicType> typeList = new ArrayList<DynamicType>(Arrays.asList(getQuery().getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE)));
        typeList.addAll(Arrays.asList(getQuery().getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON)));
        dynamicTypeSelect = new ListField<DynamicType>(sm, "dynamic_type",true );
        dynamicTypeSelect.setVector( typeList );
        rootCategory = getQuery().getSuperCategory();

        categorySelect = new CategorySelectField(sm,"choose_root_category"
                                                 ,rootCategory);
        categorySelect.setUseNull(false);
        defaultSelectCategory = new CategorySelectField(sm,"default"
                ,rootCategory);
        defaultSelectText = new TextField(sm,"default");
        addCopyPaste( defaultSelectNumber.getNumberField());
        addCopyPaste( expectedRows.getNumberField());
        addCopyPaste( expectedColumns.getNumberField());

        defaultSelectBoolean = new BooleanField(sm, "default");
        defaultSelectDate = createRaplaCalendar();
        defaultSelectDate.setNullValuePossible( true);
        defaultSelectDate.setDate( null);
        multiSelect = new BooleanField(sm,"multiselect");
        double fill = TableLayout.FILL;
        double pre = TableLayout.PREFERRED;
        panel.setLayout( new TableLayout( new double[][]
            {{5, pre, 5, fill },  // Columns
             {5, pre ,5, pre, 5, pre, 5, pre, 5, pre, 5, pre, 5,pre, 5}} // Rows
                                          ));
        panel.add("1,1,l,f", nameLabel);
        panel.add("3,1,f,f", name.getComponent() );
        panel.add("1,3,l,f", keyLabel);
        panel.add("3,3,f,f", key.getComponent() );
        panel.add("1,5,l,f", typeLabel);
        panel.add("3,5,l,f", classSelect);
        panel.add("1,7,l,t", categoryLabel);
        panel.add("3,7,l,t", categorySelect.getComponent());
        panel.add("1,7,l,t", dynamicTypeLabel);
        panel.add("3,7,l,t", dynamicTypeSelect.getComponent());
        panel.add("1,7,l,t", expectedRowsLabel);
        panel.add("3,7,l,t", expectedRows);
        panel.add("1,9,l,t", expectedColumnsLabel);
        panel.add("3,9,l,t", expectedColumns);
        panel.add("1,9,l,t", multiSelectLabel);
        panel.add("3,9,l,t", multiSelect.getComponent());
        panel.add("1,11,l,t", defaultLabel);
        panel.add("3,11,l,t", defaultSelectCategory.getComponent());
        panel.add("3,11,l,t", defaultSelectText.getComponent());
        panel.add("3,11,l,t", defaultSelectBoolean.getComponent());
        panel.add("3,11,l,t", defaultSelectDate);
        panel.add("3,11,l,t", defaultSelectNumber);
        panel.add("1,13,l,t", tabLabel);
        panel.add("3,13,l,t", tabSelect);


        setModel();

        nameLabel.setText(getString("name") + ":");
        keyLabel.setText(getString("key") +" *"+ ":");
        typeLabel.setText(getString("type") + ":");
        categoryLabel.setText(getString("root") + ":");
        dynamicTypeLabel.setText(getString("root") + ":");
        expectedRowsLabel.setText(getString("expected_rows") + ":");
        expectedColumnsLabel.setText(getString("expected_columns") + ":");
        tabLabel.setText(getString("edit-view") + ":");
        multiSelectLabel.setText("Multiselect:");
        defaultLabel.setText(getString("default") +":");
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
        expectedRows.addChangeListener( this );
        
        expectedColumns.addChangeListener( this );
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

    public void setEditKeys(boolean editKeys) {
        keyLabel.setVisible( editKeys );
        key.getComponent().setVisible( editKeys );
    }

    public JComponent getComponent() {
        return panel;
    }

    private void clearValues() {
    	 categorySelect.setValue(null);
         expectedRows.setNumber(DEFAULT_ROWS);
         expectedColumns.setNumber(DEFAULT_COLUMNS);
         defaultSelectCategory.setValue( null);
         defaultSelectText.setValue("");
         defaultSelectBoolean.setValue( null);
         defaultSelectNumber.setNumber(null);
         defaultSelectDate.setDate(null);
         multiSelect.setValue( Boolean.FALSE);
	}

    public void mapFrom(Attribute attribute) {
    	clearValues();
        try {
            mapping = true;
            clearValues();
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
            	String multiSelectValue = attribute.getAnnotation(AttributeAnnotations.KEY_MULTI_SELECT, "false") ;
            	multiSelect.setValue( multiSelectValue.equals("true") ? Boolean.TRUE : Boolean.FALSE );
            }
            Long rows = new Long(attribute.getAnnotation(AttributeAnnotations.KEY_EXPECTED_ROWS, "1"));
            expectedRows.setNumber( rows );
            Long columns = new Long(attribute.getAnnotation(AttributeAnnotations.KEY_EXPECTED_COLUMNS, String.valueOf(TextField.DEFAULT_LENGTH)));
            expectedColumns.setNumber( columns );
            
            String selectedTab = attribute.getAnnotation(AttributeAnnotations.KEY_EDIT_VIEW, AttributeAnnotations.VALUE_EDIT_VIEW_MAIN);
            tabSelect.setSelectedItem(getString(selectedTab));
            update();
        } finally {
            mapping = false;
        }
    }

    public void mapTo(Attribute attribute) throws RaplaException {
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
            String value = multiSelect.getValue()? "true":"false";
            attribute.setAnnotation(AttributeAnnotations.KEY_MULTI_SELECT, value);
        }
        else
        {
        	attribute.setAnnotation(AttributeAnnotations.KEY_MULTI_SELECT, null);
        	String value = multiSelect.getValue()? "true":"false";
        	attribute.setAnnotation(AttributeAnnotations.KEY_MULTI_SELECT, value);
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
        
        if (type.equals(AttributeType.STRING)) {
            Long size = (Long) expectedRows.getNumber();
            String newRows = null;
            if ( size != null && size.longValue() > 1)
                newRows = size.toString();

            size = (Long) expectedColumns.getNumber();
            String newColumns = null;
            if ( size != null && size.longValue() > 1)
                newColumns = size.toString();
            Object defaultValue = defaultSelectText.getValue();
            if ( defaultValue != null && defaultValue.toString().length() == 0)
            {
            	defaultValue = null;
            }
            attribute.setDefaultValue( defaultValue);
            attribute.setAnnotation(AttributeAnnotations.KEY_EXPECTED_ROWS ,  newRows);
            attribute.setAnnotation(AttributeAnnotations.KEY_EXPECTED_COLUMNS,  newColumns);
        } else {
            attribute.setAnnotation(AttributeAnnotations.KEY_EXPECTED_ROWS,  null);
            attribute.setAnnotation(AttributeAnnotations.KEY_EXPECTED_COLUMNS,  null);
        }

        String selectedTab = tabs[tabSelect.getSelectedIndex()];
        if ( selectedTab != null && !selectedTab.equals(AttributeAnnotations.VALUE_EDIT_VIEW_MAIN)) {
            attribute.setAnnotation(AttributeAnnotations.KEY_EDIT_VIEW,  selectedTab);
        } else {
            attribute.setAnnotation(AttributeAnnotations.KEY_EDIT_VIEW,  null);
        }
    }

    private void update() {
        AttributeType type = types[classSelect.getSelectedIndex()];
        boolean categoryVisible = type.equals(AttributeType.CATEGORY);
        boolean allocatableVisible = type.equals(AttributeType.ALLOCATABLE);
        final boolean textVisible = type.equals(AttributeType.STRING);
        final boolean booleanVisible = type.equals(AttributeType.BOOLEAN);
        final boolean numberVisible = type.equals(AttributeType.INT);
        final boolean dateVisible  = type.equals(AttributeType.DATE);
        boolean expectedRowsVisible = textVisible;
        boolean expectedColumnsVisible = textVisible;
        categoryLabel.setVisible( categoryVisible );
        categorySelect.getComponent().setVisible( categoryVisible );
        dynamicTypeLabel.setVisible( allocatableVisible);
        dynamicTypeSelect.getComponent().setVisible( allocatableVisible);
        expectedRowsLabel.setVisible( expectedRowsVisible );
        expectedRows.setVisible( expectedRowsVisible );
        expectedColumnsLabel.setVisible( expectedColumnsVisible );
        expectedColumns.setVisible( expectedColumnsVisible );
        defaultLabel.setVisible( !allocatableVisible);
        defaultSelectCategory.getComponent().setVisible( categoryVisible);
        defaultSelectText.getComponent().setVisible( textVisible);
        defaultSelectBoolean.getComponent().setVisible( booleanVisible);
        defaultSelectNumber.setVisible( numberVisible);
        defaultSelectDate.setVisible( dateVisible);
        multiSelectLabel.setVisible( categoryVisible || allocatableVisible);
        multiSelect.getComponent().setVisible( categoryVisible || allocatableVisible);
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
        update();
    }


	public void stateChanged(ChangeEvent e) {
        if (mapping)
            return;

        fireContentChanged();
    }

  
}

