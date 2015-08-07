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
package org.rapla.entities.storage;

import org.rapla.entities.Entity;


/** transforms ids into references to
 * the corresponding objects.
 * @see org.rapla.entities.storage.internal.ReferenceHandler;
 */

public interface EntityReferencer
{
    void setResolver( EntityResolver resolver);
    /**Return all References of the object*/
    Iterable<ReferenceInfo> getReferenceInfo();
    /** returns if the entity is refering to the Object. */
    
    public class ReferenceInfo
    {
        final private String id;
        final private Class<? extends Entity> type;

        public ReferenceInfo(String id, Class<? extends Entity> type) {
            super();
            this.id = id;
            this.type = type;
        }

        public String getId() {
            return id;
        }

        public Class<? extends Entity> getType() {
            return type;
        }
        
        @Override
        public boolean equals(Object obj) {
            if ( ! (obj instanceof ReferenceInfo))
            {
                return false;
            }
            return this.id.equals(((ReferenceInfo)obj).id);
        }
        
        @Override
        public int hashCode() 
        {
            return id.hashCode();
        }

        @Override
        public String toString() {
            return type + ":" + id;
        }

        public boolean isReferenceOf(Entity object) {
            return id.equals(object.getId() );
        }

        
    }
}




