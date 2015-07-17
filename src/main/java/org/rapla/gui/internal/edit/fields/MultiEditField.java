package org.rapla.gui.internal.edit.fields;

//Interface for one EditField; it is designed for showing multiple values at the same time
public interface MultiEditField {
//	shows the field a place holder for different values or a common value for all objects?
	public boolean hasMultipleValues();
	
//	sets on a field a place holder for different values
	public void setFieldForMultipleValues();
}