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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
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


public class ClassificationEditUI extends AbstractEditUI<Classification> {
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
			return null;
		}
	}

	protected Attribute getAttribute(int i) {
		// collection of all attributes for the deposited classifications for a
		// certain field
		Set<Attribute> attributes = new HashSet<Attribute>();
		for (Classification c : objectList) {
			String fieldName = ((AbstractEditField) fields.get(i)).getFieldName();
			Attribute attribute = c.getAttribute(fieldName);
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
    			if ( attribute.getAnnotation(AttributeAnnotations.KEY_MULTI_SELECT, "false").equals("true"))
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
			if ( attribute.getAnnotation(AttributeAnnotations.KEY_MULTI_SELECT, "false").equals("true"))
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
		String key = attribute.getKey();
		SetGetField<?> field = null;

		RaplaContext context = getContext();
		if (type.equals(AttributeType.STRING)) {
			Integer rows = new Integer(attribute.getAnnotation(	AttributeAnnotations.KEY_EXPECTED_ROWS, "1"));
			Integer columns = new Integer(attribute.getAnnotation( AttributeAnnotations.KEY_EXPECTED_COLUMNS,String.valueOf(TextField.DEFAULT_LENGTH)));
			field = new TextField(context, key, rows.intValue(),columns.intValue());
		} else if (type.equals(AttributeType.INT)) {
			field = new LongField(context, key);
		} else if (type.equals(AttributeType.DATE)) {
			field = new DateField(context, key);
		} else if (type.equals(AttributeType.BOOLEAN)) {
			field = new BooleanField(context, key);
		} else if (type.equals(AttributeType.ALLOCATABLE)) {
			DynamicType dynamicTypeConstraint = (DynamicType)attribute.getConstraint( ConstraintIds.KEY_DYNAMIC_TYPE);
			boolean multipleSelectionPossible = attribute.getAnnotation(AttributeAnnotations.KEY_MULTI_SELECT, "false").equals("true");
	//		 if (dynamicTypeConstraint == null || multipleSelectionPossible) {
				 AllocatableSelectField allocField = new AllocatableSelectField(context, key, dynamicTypeConstraint);
				 allocField.setMultipleSelectionPossible( multipleSelectionPossible);
				 field = allocField;
//			 }else {
//				 AllocatableListField allocField = new AllocatableListField(context, key, dynamicTypeConstraint);
//				 field = allocField;
//			 }
			 
		} else if (type.equals(AttributeType.CATEGORY)) {
			Category defaultCategory = (Category) attribute.defaultValue();
			Category rootCategory = (Category) attribute.getConstraint(ConstraintIds.KEY_ROOT_CATEGORY);
			boolean multipleSelectionPossible = attribute.getAnnotation(AttributeAnnotations.KEY_MULTI_SELECT, "false").equals("true");
            if (rootCategory.getDepth() > 2 || multipleSelectionPossible) {
                CategorySelectField catField = new CategorySelectField(context, key, rootCategory, defaultCategory);
                catField.setMultipleSelectionPossible( multipleSelectionPossible);
                field = catField;
            } else {
			    CategoryListField catField = new CategoryListField(context, key, rootCategory);
			    field = catField;
            }
		}
		Assert.notNull(field, "Unknown AttributeType");
		return field;
	}

	public void setObjects(List<Classification> classificationList) throws RaplaException {
		this.objectList = classificationList;
		// determining of the DynmicTypes from the classifications
		Set<DynamicType> types = new HashSet<DynamicType>();
		for (Classification c : objectList) {
			types.add(c.getType());
		}
		// checks if there is a common DynmicType
		if (types.size() == 1) {
			// read out attributes for this DynmicType
			Attribute[] attributes = types.iterator().next().getAttributes();
			// create fields for attributes
			List<SetGetField<?>> fields= new ArrayList<SetGetField<?>>();
			for (Attribute attribute:attributes) {
			    SetGetField<?> field = createField(attribute);
				//field.setUser(classificationList);
				fields.add( field);
			}
			// show fields
			setFields(fields);
		}
		mapFromObjects();
	}

	@Override
	public String getFieldName(EditField field) 
	{
	    String fieldName = field.getFieldName();
	    return getAttName(fieldName);
	}
	
	public void mapTo(SetGetField<?> field) {
         // checks if the EditField shows a common value
         if (field instanceof MultiEditField && ((MultiEditField) field).hasMultipleValues())
             return;
         // read out attribute value if the field shows a common value
         String fieldName = field.getFieldName();
         if ( field instanceof SetGetCollectionField)
         {
            Collection<?> values = ((SetGetCollectionField<?>) field).getValues();
            setAttValue(fieldName, values);
         }
         else
         {
             setAttValue(fieldName, field.getValue());
         }
     }

	
     public <T> void mapFrom(SetGetField<T> field ) {
         // read out attribute values
         Set<Object> values = getUniqueAttValues(field.getFieldName());
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

