/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Gereon Fassbender, Christopher Kohlhaas               |
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

public interface DynamicTypeAnnotations
{
    String KEY_NAME_FORMAT="nameformat";
    String KEY_NAME_FORMAT_PLANNING="nameformat_planning";
    String KEY_NAME_FORMAT_EXPORT="nameformat_export";
    String KEY_DESCRIPTION_FORMAT_EXPORT="descriptionformat_export";
    
    String KEY_CLASSIFICATION_TYPE="classification-type";
    String VALUE_CLASSIFICATION_TYPE_RESOURCE="resource";
    String VALUE_CLASSIFICATION_TYPE_RESERVATION="reservation";
    String VALUE_CLASSIFICATION_TYPE_PERSON="person";
    String VALUE_CLASSIFICATION_TYPE_RAPLATYPE="rapla";
    
    String KEY_COLORS="colors";
	String VALUE_COLORS_AUTOMATED = "rapla:automated";
	String VALUE_COLORS_COLOR_ATTRIBUTE = "color";
	String VALUE_COLORS_DISABLED = "rapla:disabled";
	
	String KEY_CONFLICTS="conflicts";
	String VALUE_CONFLICTS_NONE="never";
	String VALUE_CONFLICTS_ALWAYS="always";
	String VALUE_CONFLICTS_WITH_OTHER_TYPES="withOtherTypes";
	
	
	String KEY_TRANSFERED_TO_CLIENT = "transferedToClient";
	String VALUE_TRANSFERED_TO_CLIENT_NEVER = "never";
	
	String KEY_LOCATION="location";
}












