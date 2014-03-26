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

public interface Namespaces {
    String RAPLA_NS = "http://rapla.sourceforge.net/rapla";
    String RELAXNG_NS = "http://relaxng.org/ns/structure/1.0";
    String DYNATT_NS = "http://rapla.sourceforge.net/dynamictype";
    String EXTENSION_NS = "http://rapla.sourceforge.net/extension";
    String ANNOTATION_NS = "http://rapla.sourceforge.net/annotation";

    String[][] NAMESPACE_ARRAY = {
        {RAPLA_NS,"rapla"}
        ,{RELAXNG_NS,"relax"}
        ,{DYNATT_NS,"dynatt"}
        ,{EXTENSION_NS,"ext"}
        ,{ANNOTATION_NS,"doc"}
    };
}
