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

import javax.swing.JComponent;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.rapla.framework.RaplaContext;
import org.rapla.gui.RaplaGUIComponent;

/** Base class for most rapla edit fields. Provides some mapping
    functionality such as reflection invocation of getters/setters.
    A fieldName "username" will result in a getUsername() and setUsername()
    method.
*/
public abstract class AbstractEditField extends RaplaGUIComponent
    implements EditField

{
    final static int DEFAULT_LENGTH = 30;
    String fieldName;

    ArrayList<ChangeListener> listenerList = new ArrayList<ChangeListener>();

    public AbstractEditField(RaplaContext sm) {
        super(sm);
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

    abstract public JComponent getComponent();

    protected void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return this.fieldName;
    }

    public boolean isBlock() {
        return false;
    }

    public boolean isVariableSized() {
        return false;
    }

   

//    protected String setMethodName() {
//        return "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
//    }
//
//    protected String getMethodName() {
//        return "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
//    }
//
//    protected Method getMethod(String name,Object o,int params) throws RaplaException {
//        Method[] methods = o.getClass().getMethods();
//        for (int i=0;i<methods.length;i++)
//            if (methods[i].getName().equals(name) && methods[i].getParameterTypes().length==params)
//                return methods[i];
//        throw new RaplaException(new NoSuchMethodException(name));
//    }

//	public void mapTo(List list) throws RaplaException {
//		if (delegate != null) {
//			delegate.mapTo(list);
//			return;
//		}
//		
////		if the field shows just a place holder for different data -> no mapping
////		(no new consistent data)
//		if (this instanceof MultiEditField && ((MultiEditField)this).hasMultipleValues())
//			return;
//		
////		read out the field and copy to the objects
//        for (Object obj : list) {
//			Method method = getMethod(setMethodName(), obj, 1);
//			try {
//				method.invoke(obj, new Object[] { getValue() });
//			} catch (InvocationTargetException ex) {
//				throw new RaplaException(ex.getTargetException());
//			} catch (Exception ex) {
//				throw new RaplaException(ex);
//			}
//		}
//	}
//
//	public void mapFrom(List list) throws RaplaException {
//		if (delegate != null) {
//			delegate.mapFrom(list);
//			return;
//		}
//
//		Set<Object> values = new HashSet<Object>();
//		
//
////		collection of all attribute data from the committed objects
//        for (Object obj : list) {
//			Method method = getMethod(getMethodName(), obj, 0);
//			try {
//				values.add(method.invoke(obj, new Object[] {}));
//			} catch (InvocationTargetException ex) {
//				throw new RaplaException(ex.getTargetException());
//			} catch (Exception ex) {
//				throw new RaplaException(ex);
//			}
//
//			if (values.size() == 0) {
//				return;
////			checks if there is a consistent data
//			} else if (values.size() == 1) {
////				apply date to field
//				Object value = values.iterator().next();
//				setValue(value);
//			} else if (this instanceof MultiEditField){
////				... otherwise apply place holder to field
//				((MultiEditField)this).setFieldForMultipleValues();
//			}
//		}
//	}

  

}

