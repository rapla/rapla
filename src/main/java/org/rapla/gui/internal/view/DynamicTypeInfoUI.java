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

import org.rapla.entities.Category;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.framework.RaplaContext;

class DynamicTypeInfoUI extends HTMLInfo<DynamicType> {
    public DynamicTypeInfoUI(RaplaContext sm) {
        super(sm);
    }

    protected String createHTMLAndFillLinks(DynamicType object,LinkController controller){
        DynamicType dynamicType = object;
        StringBuffer buf = new StringBuffer();
        insertModificationRow( object, buf );
        Collection<Row> att = new ArrayList<Row>();
        att.add(new Row(getString("dynamictype.name"), strong( encode( getName( dynamicType ) ))));
        Attribute[] attributes = dynamicType.getAttributes();
        for (int i=0;i<attributes.length;i++) {
            String name = getName(attributes[i]);
            String type = getString("type." + attributes[i].getType());
            if (attributes[i].getType().equals(AttributeType.CATEGORY)) {
                Category category = (Category) attributes[i].getConstraint(ConstraintIds.KEY_ROOT_CATEGORY);
                if (category.getParent()!=null)
                    type = type + " " +getName(category);
            }
            att.add(new Row(encode(name), encode(type)));
        }
        createTable(att, buf, false);
        return buf.toString();
    }
    
    protected String getTooltip(DynamicType object) {
        if ( this.isAdmin()) {
            return createHTMLAndFillLinks( object, null);
        } else {
            return null;
        }
    }
}


