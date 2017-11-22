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
package org.rapla.entities.dynamictype;

import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsType;

import java.util.Iterator;

/** <p>A new ClassificationFilter for a classifications belonging to the
    same DynamicType can be created by the newClassificationFilter of
    the corresponding DynamicType object.
    </p>

    <p>You can set rules for the attributes of the DynamicType.  A
    Classification (object implementing Classifiable) is matched by
    the filter when the conditions for each attribute-rule are
    matched (AND - function).</p>

    <p>A condition is an array of size 2, the first field contains the 
      operator of the condition and the second the test value. 
      When an attribute-rule has more than one condition, at least
      one of the conditions must be matched (OR - function ) .
    </p>
    <p>
      The following Example matches all classifications
      with a title-value that contains either "rapla" or "sourceforge"
      ,a size-value that is &gt; 5 and a category-department-value that
      is either the departmentA or the departmentB.
    </p>

    <pre>
       DynamicType eventType = facade.getDynamicType("event");
       ClassificationFilter f = eventType.newClassificationFilter();
       f.addRule(
                 "title"
                 ,new Object {
                    {"contains", "rapla"}
                    ,{"contains", "sourceforge"}
                  });
      f.addRule(
                 "size"
                 ,new Object{
                      {"&gt;", new Integer(5)}
                  });

	   Category departemntCategory = facade.getRootCategory().getCategory("departments");
       Category departmentA = departmentCategory.getCategory("departmentA");
       Category departmentB = departmentCategory.getCategory("departmentB");
       f.addRule(
                 "department"
                 ,new Object{
                      {"=", departmentA}
                     ,{ "=", departmentB}
                  });
    </pre>

    @see Classification
 */
@JsType
public interface ClassificationFilter extends Cloneable {
    DynamicType getType();

    /** Defines a rule for the passed attribute. 
     */
    void setRule(int index, String attributeName,Object[][] conditions);
    @JsMethod(name = "setRuleWithAttribute")
    void setRule(int index, Attribute attribute,Object[][] conditions);
    /** appends a rule. 
     *  @see #setRule*/
    void addRule(String attributeName,Object[][] conditions);
    
    /** shortcut to 
     * <pre>
     * f.addRule(
                 attributeName
                 ,new Object{
                            {"=", object}}
                  });
     * </pre>
     * @param attributeName
     * @param object
     */
    void addEqualsRule( String attributeName,Object object);
    /** shortcut to 
     * <pre>
     * f.addRule(
                 attributeName
                 ,new Object{
                            {"is", object}}
                  });
     * </pre>
     * @param attributeName
     * @param object
     */
    void addIsRule( String attributeName,Object object);
    int ruleSize();
    Iterator<? extends ClassificationFilterRule> ruleIterator();
    void removeAllRules();
    void removeRule(int index);

    boolean matches(Classification classification);
    ClassificationFilter clone();
    
    ClassificationFilter[] CLASSIFICATIONFILTER_ARRAY = new ClassificationFilter[0];
    ClassificationFilter[] toArray();

	class Util
	{
		static public boolean matches(ClassificationFilter[] filters,
				Classifiable classifiable) 
		{
			Classification classification = classifiable.getClassification();
			for (ClassificationFilter filter:filters) {
				if (filter.matches(classification)) {
					return true;
				}
			}
			return false;
		}
	}
}

