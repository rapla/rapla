
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
package org.rapla.gui.internal.view;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeAnnotations;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.internal.AttributeImpl;
import org.rapla.framework.RaplaContext;

class ClassificationInfoUI<T extends Classifiable> extends HTMLInfo<T> {
    public ClassificationInfoUI(RaplaContext sm) {
        super(sm);
    }

    public void insertClassificationTitle( Classifiable classifiable, StringBuffer buf ) {
        Classification classification = classifiable.getClassification();
        buf.append( "<strong>");
        Locale locale = getRaplaLocale().getLocale();
        encode( classification.getType().getName(locale), buf );
        buf.append( "</strong>");
    }

    protected void insertClassification( Classifiable classifiable, StringBuffer buf ) {
        insertClassificationTitle( classifiable, buf );
        Collection<Row> att = new ArrayList<Row>();
        att.addAll(getClassificationAttributes(classifiable, false, null));
        createTable(att,buf,false);
    }

    protected Collection<HTMLInfo.Row> getClassificationAttributes(Classifiable classifiable, boolean excludeAdditionalInfos, LinkController controller) {
        Collection<Row> att = new ArrayList<Row>();
        Classification classification = classifiable.getClassification();

        Attribute[] attributes = classification.getAttributes();
        for (int i=0; i< attributes.length; i++) {
            Attribute attribute = attributes[i];
            String view = attribute.getAnnotation( AttributeAnnotations.KEY_EDIT_VIEW, AttributeAnnotations.VALUE_EDIT_VIEW_MAIN );
            if ( view.equals(AttributeAnnotations.VALUE_EDIT_VIEW_NO_VIEW )) {
                continue;
            }
            if ( excludeAdditionalInfos && !view.equals( AttributeAnnotations.VALUE_EDIT_VIEW_MAIN ) ) {
                continue;
            }
            Collection<Object> values = classification.getValues( attribute);
            
            /*
            if (value == null)
                continue;
			*/	
            String name = getName(attribute);
            String valueString = null;
            Locale locale = getRaplaLocale().getLocale();
            String pre = name;
            for (Object value:values)
            {
	            if (value instanceof Boolean) {
	                valueString = getString(((Boolean) value).booleanValue() ? "yes":"no");
	            }
	            if (value instanceof Allocatable && controller != null) {
	                Allocatable allocatable = (Allocatable) value;
	                StringBuffer buf = new StringBuffer();
	                controller.createLink(allocatable,encode(getName(allocatable)),buf);
	                valueString = buf.toString();
	            } else {
	            	valueString = encode(((AttributeImpl)attribute).getValueAsString( locale, value));
	            }
	            att.add (new Row(pre,valueString));
	            pre = "";
            }
        }
        return att;
    }
    
    @Override
    protected String getTooltip(Classifiable classifiable) {
        StringBuffer buf = new StringBuffer();
        Collection<Row> att = new ArrayList<Row>();
        att.addAll(getClassificationAttributes(classifiable, false,null));
        createTable(att,buf,false);
        return buf.toString();
    }

    @Override
    protected String createHTMLAndFillLinks(Classifiable classifiable,LinkController controller) {
        StringBuffer buf = new StringBuffer();
        insertClassification( classifiable, buf );
        return buf.toString();
    }
   
    /**
     * @param object
     * @param controller
     * @return
     */
    protected String getTitle(Object object, LinkController controller)  {
        Classifiable classifiable = (Classifiable) object;
        Classification classification = classifiable.getClassification();
        return 	 getString("view") + ": " 	+ classification.getType().getName(getLocale());
    }
    
   

}


