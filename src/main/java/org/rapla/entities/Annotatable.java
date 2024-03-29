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
package org.rapla.entities;

public interface Annotatable {
    void setAnnotation(String key, String annotation) throws IllegalAnnotationException;
    //<T extends RaplaAnnotation> String getAnnotation(Class<T> annotation);
    //<T extends RaplaAnnotation> String  getAnnotation(Class<T> annotation, T defaultValue);
    String getAnnotation(String key);
    String getAnnotation(String key, String defaultValue);
    String[] getAnnotationKeys();
}




