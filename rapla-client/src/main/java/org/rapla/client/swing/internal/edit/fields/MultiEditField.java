package org.rapla.client.swing.internal.edit.fields;

//Interface for one EditField; it is designed for showing multiple values at the same time
public interface MultiEditField {
//	shows the field a place holder for different values or a common value for all objects?
boolean hasMultipleValues();
	
//	sets on a field a place holder for different values
void setFieldForMultipleValues();
}