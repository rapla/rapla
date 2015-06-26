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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.rapla.components.util.Assert;
import org.rapla.entities.Category;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeAnnotations;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.EditField;
import org.rapla.gui.internal.edit.fields.AllocatableSelectField;
import org.rapla.gui.internal.edit.fields.BooleanField;
import org.rapla.gui.internal.edit.fields.CategoryListField;
import org.rapla.gui.internal.edit.fields.CategorySelectField;
import org.rapla.gui.internal.edit.fields.DateField;
import org.rapla.gui.internal.edit.fields.LongField;
import org.rapla.gui.internal.edit.fields.MultiEditField;
import org.rapla.gui.internal.edit.fields.SetGetCollectionField;
import org.rapla.gui.internal.edit.fields.SetGetField;
import org.rapla.gui.internal.edit.fields.TextField;


public class ClassificationEditUI extends AbstractEditUI<Classification> {
    String selectedView = AttributeAnnotations.VALUE_EDIT_VIEW_MAIN;
	
    public String getSelectedView()
    {
        return selectedView;
    }

    public void setSelectedView(String selectedView)
    {
        this.selectedView = selectedView;
    }

    public ClassificationEditUI(RaplaContext sm) {
		super(sm);
	}

	// enhanced to an array, for administration of multiple classifications

	private String getAttName(String key) {
		// collection of all attribute-names for the deposited classifications
		Set<String> attNames = new HashSet<String>();
		for (Classification c : objectList) {
			attNames.add(getName(c.getAttribute(key)));
		}
		// checks if there is a common attribute-name
		if (attNames.size() == 1) {
			// delivers this name
			return attNames.iterator().next();
		} else {
			return "-";
		}
	}

	protected Attribute getAttribute(int i) {
		// collection of all attributes for the deposited classifications for a
		// certain field
		Set<Attribute> attributes = new HashSet<Attribute>();
		for (Classification c : objectList) {
			String key = getKey( fields.get(i));
			Attribute attribute = c.getAttribute(key);
			attributes.add(attribute);
		}

		// check if there is a common attribute
		if (attributes.size() == 1) {
			// delivers this attribute
			return attributes.iterator().next();
		} else {
			return null;
		}
	}

	protected void setAttValue(String key, Object value) {
		// sets the attribute value for all deposited classifications
		for (Classification c : objectList) {
			Attribute attribute = c.getAttribute(key);
			if ( value instanceof Collection<?>)
			{
			    Collection<?> collection = (Collection<?>)value;
			    Boolean multiSelect = (Boolean)attribute.getConstraint(ConstraintIds.KEY_MULTI_SELECT);
			    if ( multiSelect != null && multiSelect==true)
                {
                    c.setValues(attribute, collection);
                }
    			else if ( collection.size() > 0) 
    			{
    			     c.setValue(attribute, collection.iterator().next());
    			}
    			else
    			{
                    c.setValue(attribute, null);
    			}
			}
			else
			{
			     c.setValue(attribute, value);
			}
    
		}
	}

    public Set<Object> getUniqueAttValues(String key) {
        // collection of all attribute values for a certain attribute
		Set<Object> values = new LinkedHashSet<Object>();
		for (Classification c : objectList) {
			Attribute attribute = c.getAttribute(key);
			Object value;
			Boolean multiSelect = (Boolean) attribute.getConstraint(ConstraintIds.KEY_MULTI_SELECT);
			if ( multiSelect != null && multiSelect == true)
			{
				value = c.getValues(attribute);
			}
			else
			{
				value = c.getValue(attribute);
			}
			values.add(value);
		}
        return values;
    }

	private SetGetField<?> createField(Attribute attribute)  {
		AttributeType type = attribute.getType();
		String label = getAttName(attribute.getKey());
		SetGetField<?> field = null;

		RaplaContext context = getContext();
		if (type.equals(AttributeType.STRING)) {
			Integer rows = new Integer(attribute.getAnnotation(	AttributeAnnotations.KEY_EXPECTED_ROWS, "1"));
			Integer columns = new Integer(attribute.getAnnotation( AttributeAnnotations.KEY_EXPECTED_COLUMNS,String.valueOf(TextField.DEFAULT_LENGTH)));
			TextField textField = new TextField(context, label, rows.intValue(),columns.intValue());
            boolean isColor = attribute.getKey().equals("color") || attribute.getAnnotation(AttributeAnnotations.KEY_COLOR, "false").equals("true");
            textField.setColorPanel(  isColor);
			field = textField;
		} else if (type.equals(AttributeType.INT)) {
			field = new LongField(context, label);
		} else if (type.equals(AttributeType.DATE)) {
			field = new DateField(context, label);
		} else if (type.equals(AttributeType.BOOLEAN)) {
			field = new BooleanField(context, label);
		} else if (type.equals(AttributeType.ALLOCATABLE)) {
			DynamicType dynamicTypeConstraint = (DynamicType)attribute.getConstraint( ConstraintIds.KEY_DYNAMIC_TYPE);
			Boolean multipleSelectionPossible = (Boolean) attribute.getConstraint(ConstraintIds.KEY_MULTI_SELECT);
	//		 if (dynamicTypeConstraint == null || multipleSelectionPossible) {
				 AllocatableSelectField allocField = new AllocatableSelectField(context,  dynamicTypeConstraint);
				 allocField.setFieldName(label);
				 allocField.setMultipleSelectionPossible( multipleSelectionPossible != null ? multipleSelectionPossible : false);
				 field = allocField;
//			 }else {
//				 AllocatableListField allocField = new AllocatableListField(context, key, dynamicTypeConstraint);
//				 field = allocField;
//			 }
			 
		} else if (type.equals(AttributeType.CATEGORY)) {
			Category defaultCategory = (Category) attribute.defaultValue();
			Category rootCategory = (Category) attribute.getConstraint(ConstraintIds.KEY_ROOT_CATEGORY);
			Boolean multipleSelectionPossible = (Boolean) attribute.getConstraint(ConstraintIds.KEY_MULTI_SELECT);
            if (rootCategory.getDepth() > 2 || multipleSelectionPossible) {
                CategorySelectField catField = new CategorySelectField(context, rootCategory, defaultCategory);
                catField.setMultipleSelectionPossible( multipleSelectionPossible != null ? multipleSelectionPossible : false);
                catField.setFieldName( label );
                field = catField;
            } else {
			    CategoryListField catField = new CategoryListField(context,  rootCategory);
			    catField.setFieldName( label );
			    field = catField;
            }
		}
		Assert.notNull(field, "Unknown AttributeType");
		return field;
	}

	Map<EditField,String> fieldKeyMap = new HashMap<EditField,String>();
	
	public void setObjects(List<Classification> classificationList) throws RaplaException {
		this.objectList = classificationList;
		recreateFields();
	}

    public void recreateFields() throws RaplaException
    {
        // determining of the DynmicTypes from the classifications
		Set<DynamicType> types = new HashSet<DynamicType>();
		for (Classification c : objectList) {
			types.add(c.getType());
		}
		// checks if there is a common DynmicType
		if (types.size() == 1) {
		    fieldKeyMap.clear();
			// read out attributes for this DynmicType
			Attribute[] attributes = types.iterator().next().getAttributes();
			// create fields for attributes
			List<SetGetField<?>> fields= new ArrayList<SetGetField<?>>();
			for (Attribute attribute:attributes) {
			    boolean isVisible = isVisible(attribute);
			    if ( !isVisible)
			    {
			        continue;
			    }

			    SetGetField<?> field = createField(attribute);
				//field.setUser(classificationList);
				fields.add( field);
				fieldKeyMap.put( field, attribute.getKey());
			}
			// show fields
			setFields(fields);
		}
        mapFromObjects();
    }

    protected boolean isVisible(Attribute attribute)
    {
        String view = attribute.getAnnotation(AttributeAnnotations.KEY_EDIT_VIEW, AttributeAnnotations.VALUE_EDIT_VIEW_MAIN);
        boolean isVisible = view.equals( getSelectedView() ) || view.equals( AttributeAnnotations.VALUE_EDIT_VIEW_MAIN);
        return isVisible;
    }

	public void mapTo(SetGetField<?> field) {
         // checks if the EditField shows a common value
         if (field instanceof MultiEditField && ((MultiEditField) field).hasMultipleValues())
             return;
         // read out attribute value if the field shows a common value
         String attKey = getKey(field);
         if ( field instanceof SetGetCollectionField)
         {
            Collection<?> values = ((SetGetCollectionField<?>) field).getValues();
            setAttValue(attKey, values);
         }
         else
         {
             setAttValue(attKey, field.getValue());
         }
     }
	
	protected String getKey(EditField field)
	{
	    String key = fieldKeyMap.get( field);
        return key;
	}
	
     public <T> void mapFrom(SetGetField<T> field ) {
         // read out attribute values
         Set<Object> values = getUniqueAttValues(getKey(field));
         // checks if there is a common value, otherwise a place holder has
         // to be shown for this field
         if ( values.size() > 1 && field instanceof MultiEditField)
         {
             // shows place holder
             ((MultiEditField) field).setFieldForMultipleValues();
         }
         else if ( values.size() == 1)
         {
             // set common value
             Object first =  values.iterator().next();
             if ( first instanceof Collection)
             {
                 @SuppressWarnings("unchecked")
                 Collection<T> list = (Collection<T>)first;
                 if ( field instanceof SetGetCollectionField)
                 {
                    @SuppressWarnings("unchecked")
                    SetGetCollectionField<T> setGetCollectionField = (SetGetCollectionField<T>)field;
                    setGetCollectionField.setValues(list);
                 }
                 else if ( list.size() > 0)
                 {
                     field.setValue( list.iterator().next());
                 }
                 else
                 {
                     field.setValue( null);
                 }
             }
             else
             {
                 @SuppressWarnings("unchecked")
                 T casted = (T)first;
                 field.setValue( casted);
             }
         }
         else
         {
             field.setValue(null);
         }
     }

    public void mapToObjects() throws RaplaException 
    {
        for (EditField field: fields)
        {
            SetGetField<?> f = (SetGetField<?>) field;
            mapTo( f);
        }
    }

    protected void mapFromObjects() throws RaplaException {
        for (EditField field: fields)
        {
            SetGetField<?> f = (SetGetField<?>) field;
            mapFrom( f);
        }
    }
    
    
    

}

