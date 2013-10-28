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
package org.rapla.entities.storage.internal;

import org.rapla.entities.RaplaType;


/*
An identifier could be something like a URI. It is used for:
<ol>
  <li>Lookup or store the identified objects.</li>
  <li>Distinct the identified objects: Two objects are identical if and
  only if obj1.getId().equals(obj2.getId()).
  </li>
  <li>Serialize/Deserialize relationsships (e.g. references) between
  objects.</li>
</ol>
Two conditions should hold for all identifiers:
<ol>
  <li>An identifier is an immutable object.</li>

  <li>Every object that has got an identifier should keep it for it's
  lifetime. There is one exception: If it is possible to
  serialize/deserialize the object-map that no relationship
  information get's lost and obj1.getId().equals(obj2.getId()) returns
  the same information with the new ids. This exception is important,
  if we want to serialize data to an XML-File.</li>
</ol>
*/
public class SimpleIdentifier implements Comparable<SimpleIdentifier>
{
    
    String type = null;
    int key;
    
    public SimpleIdentifier(RaplaType type,int key) {
        this.type = type.toString().intern();
        this.key = key;
    }

    public boolean equals(Object o) {
        if ( o == null)
        {
            return false;
        }
        if ( o== this)
        {
        	return true;
        }
        if ( !(o instanceof SimpleIdentifier))
        {
        	return false;
        }
        SimpleIdentifier ident = (SimpleIdentifier)o;
        return (ident.key == key && ident.type == type);
    }

    public int hashCode() {
        int typeHc;
        if ( type != null) {
            typeHc = type.hashCode();
        } else {
            typeHc = 0;
        }
        return  typeHc+ typeHc * key;
    }

    public int getKey() {
        return key;
    }

    public String getTypeName() {
        return type;
    }

    public String toString() {
        return type + "_" + key;
    }

	public int compareTo(SimpleIdentifier id2) {
		SimpleIdentifier id1 = this;
		if ( id1 == id2 || id1.equals(id2))
		{
			return 0; 
		}
	    return (id1.getKey() < id2.getKey()) ? -1 : 1;
	}

}




