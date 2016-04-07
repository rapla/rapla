/*--------------------------------------------------------------------------*
  | Copyright (C) 2014 Christopher Kohlhaas                                  |
  |                                                                          |
  | This program is free software; you can redistribute it and/or modify     |
  | it under the terms of the GNU General Public License as published by the |
  | Free Software Foundation. A copy of the license has been included with   |
  | these distribution in the COPYING file, if not go to www.fsf.org .       |
  |                                                                          |
  | As a special exception, you are granted the permissions to link this     |
  | program with every library, which license fulfills the Open Source       |
  | Definition as published by the Open Source Initiative (OSI).             |
  *--------------------------------------------------------------------------*/

package org.rapla.storage.xml;

import java.io.IOException;
import java.util.Iterator;

import org.rapla.components.util.Assert;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.ClassificationFilterRule;
import org.rapla.framework.RaplaException;


class ClassificationFilterWriter extends RaplaXMLWriter {
    public ClassificationFilterWriter(RaplaXMLContext sm) throws RaplaException {
        super(sm);
    }

    public void printClassificationFilter(ClassificationFilter f) throws IOException,RaplaException {
        openTag("rapla:classificationfilter");
        att("dynamictype", f.getType().getKey());
        closeTag();
        for (Iterator<? extends ClassificationFilterRule> it = f.ruleIterator();it.hasNext();) {
            ClassificationFilterRule rule = it.next();
			printClassificationFilterRule(rule);
        }
        closeElement("rapla:classificationfilter");
    }

    private void printClassificationFilterRule(ClassificationFilterRule rule) throws IOException,RaplaException {
        Attribute attribute = rule.getAttribute();
        Assert.notNull( attribute );
        String[] operators = rule.getOperators();
        Object[] values = rule.getValues();
        openTag("rapla:rule");
        att("attribute", attribute.getKey());
        closeTag();
        for (int i=0;i<operators.length;i++) {
            openTag("rapla:orCond");
            att("operator", operators[i]);
            closeTagOnLine();
            if (values[i] != null)
                printAttributeValue(attribute, values[i]);
            closeElementOnLine("rapla:orCond");
            println();
        }
        closeElement("rapla:rule");
    }

}



