package org.rapla.framework;

/**
 * Could be replaced by javax.inject.qualifier
 * 
 */

@SuppressWarnings("unused")
public class TypedComponentRole<T> {
    String id;

    public TypedComponentRole(String id) {
        this.id = id.intern();
    }
    
    public String getId()
    {
        return id;
    }
    
    public String toString()
    {
        return id;
    }
    
    public boolean equals(Object obj)
    {
    	if (obj == null)
    	{
    		return false;
    	}
    	if ( !(obj instanceof TypedComponentRole))
    	{
    		return false;
    	}
    	return id.equals(obj.toString());
    }
    
    @Override
    public int hashCode() {
    	return id.hashCode();
    }
   
}
