package org.rapla.storage.xml;

import java.util.HashMap;

import org.rapla.framework.TypedComponentRole;




public class RaplaDefaultXMLContext implements RaplaXMLContext
{
    private final HashMap<String,Object> contextObjects = new HashMap<String,Object>();
    protected final RaplaXMLContext parent;

    public RaplaDefaultXMLContext()
    {
        this( null );
    }

    public RaplaDefaultXMLContext(final RaplaXMLContext parent)
    {
        this.parent = parent;
    }

	/**
	 * @throws RaplaXMLContextException
	 */
	protected Object lookup( final String key ) throws RaplaXMLContextException
    {
        return contextObjects.get( key );
    }

    protected boolean has( final String key )
    {
        return contextObjects.get( key ) != null;
    }
    
    public <T> void put(Class<T> componentRole, T instance) {
        contextObjects.put(componentRole.getName(), instance );
    }
    
    public <T> void put(TypedComponentRole<T> componentRole, T instance) {
        contextObjects.put(componentRole.getId(), instance );
    }

    public boolean has(Class<?> componentRole) {
        if  (has(componentRole.getName()))
        {
            return true;
        }
        return parent != null && parent.has( componentRole);
    }
    

    public boolean has(TypedComponentRole<?> componentRole) {
        if (has( componentRole.getId()))
        {
            return true;
        }
        return parent != null && parent.has( componentRole);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T lookup(Class<T> componentRole) throws RaplaXMLContextException
    {
        final String key = componentRole.getName();
        T lookup = (T) lookup(key);
        if ( lookup == null)
        {
            if ( parent != null)
            {
                return parent.lookup( componentRole);
            }
            else
            {
                throw new RaplaXMLContextException(  key );
            }
        }
        return lookup;
    }

    
    @SuppressWarnings("unchecked")
    public <T> T lookup(TypedComponentRole<T> componentRole) throws RaplaXMLContextException
    {
        final String key = componentRole.getId();
        T lookup = (T) lookup(key);
        if ( lookup == null)
        {
            if ( parent != null)
            {
                return parent.lookup( componentRole);
            }
            else
            {
                throw new RaplaXMLContextException(  key );
            }
        }
        return lookup;
    }

}
