
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
package org.rapla.client.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

import org.rapla.RaplaResources;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeAnnotations;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.internal.AttributeImpl;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;

public class ClassificationInfoUI<T extends Classifiable> extends HTMLInfo<T> {
    
    public ClassificationInfoUI(RaplaResources i18n, RaplaLocale raplaLocale, RaplaFacade facade, Logger logger)
    {
        super(i18n, raplaLocale, facade, logger);
    }

    public void insertClassificationTitle( Classifiable classifiable, StringBuffer buf ) {
        Classification classification = classifiable.getClassification();
        buf.append( "<strong>");
        Locale locale = getRaplaLocale().getLocale();
        encode( classification.getType().getName(locale), buf );
        buf.append( "</strong>");
    }

    public String getUsername(ReferenceInfo<User> userId)
    {
        try
        {
            String name = getFacade().getOperator().getUsername(userId);
            return name;
        }
        catch (RaplaException e)
        {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    protected Collection<HTMLInfo.Row> getClassificationAttributes(Classifiable classifiable, boolean excludeAdditionalInfos, LinkController controller, User user) {
        Collection<Row> att = new ArrayList<Row>();
        Classification classification = classifiable.getClassification();
        Attribute[] attributes = classification.getAttributes();
        for (int i=0; i< attributes.length; i++) {
            Attribute attribute = attributes[i];
            if ( user != null && !getFacade().getPermissionController().canRead( classification, attribute, user))
            {
                continue;
            }
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
    public String getTooltip(Classifiable classifiable, User user) {
        StringBuffer buf = new StringBuffer();
        Collection<Row> att = new ArrayList<Row>();
        att.addAll(getClassificationAttributes(classifiable, false,null,user));
        createTable(att,buf,false);
        return buf.toString();
    }

    @Override
    public String createHTMLAndFillLinks(Classifiable classifiable,LinkController controller, User user) {
        StringBuffer buf = new StringBuffer();
        insertClassificationTitle( classifiable, buf );
        Collection<Row> att = new ArrayList<Row>();
        att.addAll(getClassificationAttributes(classifiable, false, null, user));
        createTable(att,buf,false);
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


