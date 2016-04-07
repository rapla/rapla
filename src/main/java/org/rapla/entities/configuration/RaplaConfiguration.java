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
package org.rapla.entities.configuration;

import java.io.Serializable;

import org.rapla.entities.RaplaObject;
import org.rapla.framework.Configuration;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.TypedComponentRole;

/**
 * This class adds just the get Type method to the DefaultConfiguration so that the config can be stored in a preference object
 * @author ckohlhaas
 * @version 1.00.00
 * @since 2.03.00
 */
public class RaplaConfiguration extends DefaultConfiguration implements RaplaObject, Serializable{
   // Don't forget to increase the serialVersionUID when you change the fields
   private static final long serialVersionUID = 1;

   public RaplaConfiguration()
   {
	   super();
   }
   
   /** Creates a RaplaConfinguration with one element of the specified name 
* @param name the element name
* @param content The content of the element. Can be null.
*/
   public RaplaConfiguration( String name, String content) {
	   super(name, content);
   }
   
   public RaplaConfiguration(String localName) {
	   super(localName);
   }

   public RaplaConfiguration(Configuration configuration) {
	   super( configuration);
   }

    @Override
    public Class<? extends RaplaObject> getTypeClass()
    {
        return RaplaConfiguration.class;
    }
    
   public RaplaConfiguration replace(  Configuration oldChild, Configuration newChild) 
   {
	   return (RaplaConfiguration) super.replace( oldChild, newChild);
   }
   
   public String getChildValue(TypedComponentRole<String> key, String defaultValue )
   {
       String id = key.getId();
       Configuration child = getChild( id);
       String result = child.getValue(defaultValue);
       return result;
   }
   
   public Integer getChildValue(TypedComponentRole<Integer> key, Integer defaultValue )
   {
       String id = key.getId();
       Configuration child = getChild( id);
       Integer result = child.getValueAsInteger(defaultValue);
       return result;
   }
   
   public Boolean getChildValue(TypedComponentRole<Boolean> key, Boolean defaultValue )
   {
       String id = key.getId();
       Configuration child = getChild( id);
       Boolean result = child.getValueAsBoolean(defaultValue);
       return result;
   }
          
   @Override
   protected RaplaConfiguration newConfiguration(String localName) {
	   return new RaplaConfiguration( localName);
   }
    
   
   public RaplaConfiguration clone()
   {
	   return new RaplaConfiguration( this);
   }

    
    
}
