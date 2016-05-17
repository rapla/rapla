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

import java.util.Date;
import java.util.Map;

import org.rapla.RaplaResources;
import org.rapla.components.util.ParseDateException;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.components.util.xml.RaplaSAXAttributes;
import org.rapla.components.util.xml.RaplaSAXParseException;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.MultiLanguageName;
import org.rapla.entities.Ownable;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.internal.KeyAndPathResolver;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.entities.storage.internal.SimpleEntity;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.TypedComponentRole;
import org.rapla.logger.Logger;
import org.rapla.storage.IdCreator;
import org.rapla.storage.impl.EntityStore;

public class RaplaXMLReader extends DelegationHandler implements Namespaces
{
    public static TypedComponentRole<Double> VERSION = new TypedComponentRole<Double>("org.rapla.version");
    protected EntityStore store;
    private Logger logger;
    private IdCreator idTable;
    private Map<String,Class<? extends RaplaObject>> localnameMap;
    private Map<Class<? extends RaplaObject>,RaplaXMLReader> readerMap;
    private SerializableDateTimeFormat dateTimeFormat;
    private RaplaResources i18n;
    private Date now;
    private RaplaXMLContext context;
    private KeyAndPathResolver keyAndPathResolver;
    
    public static class TimestampDates
    {
    	public Date createTime;
    	public Date changeTime;
    }

    public Date getReadTimestamp()
    {
        return now;
    }
    
    public boolean isBefore1_2()
    {
        if (context.has(VERSION))
        {
            try {
                Double version = context.lookup( VERSION);
                return version <1.2;
            } catch (RaplaXMLContextException e) {
            }
        }
        return false;
    }
    
    public RaplaXMLReader( RaplaXMLContext context ) throws RaplaException
    {
        this.context = context;
        logger = context.lookup( Logger.class );
        this.i18n = context.lookup(RaplaResources.class);
        RaplaLocale raplaLocale = context.lookup( RaplaLocale.class );
        this.store = context.lookup( EntityStore.class);
        this.keyAndPathResolver = context.lookup(KeyAndPathResolver.class);
        this.idTable = context.lookup( IdCreator.class );
        dateTimeFormat = raplaLocale.getSerializableFormat();
        this.localnameMap = context.lookup( PreferenceReader.LOCALNAMEMAPENTRY );
        this.readerMap = context.lookup( PreferenceReader.READERMAP );
        now = new Date();
    }
    
    public TimestampDates readTimestamps(RaplaSAXAttributes atts) throws RaplaSAXParseException
    {
	    String createdAt = atts.getValue( "", "created-at");
	    String lastChanged = atts.getValue( "", "last-changed");
	    Date createTime = null;
	    Date changeTime = createTime;
	    if (createdAt != null)
	    {
	        createTime = parseTimestamp( createdAt);
	    }
	    else
	    {
	    	createTime = now;
	    }
	    if (lastChanged != null)
	    {
	    	changeTime = parseTimestamp( lastChanged);
	    }
	    else
	    {
	    	changeTime = createTime;
	    }
	    if ( changeTime.after( now) )
	    {
	        getLogger().warn("Last changed is in the future " +lastChanged  + ". Taking current time as new timestamp.");
	        changeTime = now;
	    }
	    if ( createTime.after( now) )
	    {
	        getLogger().warn("Create time is in the future " +createTime  + ". Taking current time as new timestamp.");
	        createTime = now;
	    }
	    TimestampDates result = new TimestampDates();
        result.createTime = createTime;
        result.changeTime = changeTime;
        return result;
    }
    
    public Class<? extends RaplaObject> getTypeForLocalName( String localName )
        throws RaplaSAXParseException
    {
        Class<? extends RaplaObject> type =  localnameMap.get( localName );
        if (type == null)
            throw createSAXParseException( "No type declared for localname " + localName );
        return type;
    }

    /**
     * @param raplaType
     * @throws RaplaSAXParseException
     */
    protected RaplaXMLReader getChildHandlerForType( Class<? extends RaplaObject> raplaType )
        throws RaplaSAXParseException
    {
        RaplaXMLReader childReader = readerMap.get( raplaType );
        if (childReader == null)
        {
            throw createSAXParseException( "No Reader declared for type " + raplaType );
        }
        addChildHandler( childReader );
        return childReader;
    }

    protected Logger getLogger()
    {
        return logger;
    }

    public I18nBundle getI18n() 
    {
    	return i18n;
    }
    
    

    public Long parseLong( String text ) throws RaplaSAXParseException
    {
        try
        {
            return new Long( text );
        }
        catch (NumberFormatException ex)
        {
            throw createSAXParseException( "No valid number format: " + text );
        }
    }
    
    public Boolean parseBoolean( String text ) 
    {
        return new Boolean( text );
    }

    public Date parseDate( String date, boolean fillDate ) throws RaplaSAXParseException
    {
        try
        {
            return dateTimeFormat.parseDate( date, fillDate );
        }
        catch (ParseDateException ex)
        {
            throw createSAXParseException( ex.getMessage() );
        }
    }



    public Date parseDateTime( String date, String time ) throws RaplaSAXParseException
    {
        try
        {
            return dateTimeFormat.parseDateTime( date, time );
        }
        catch (ParseDateException ex)
        {
            throw createSAXParseException( ex.getMessage() );
        }
    }

    public Date parseTimestamp( String timestamp ) throws RaplaSAXParseException
    {
        try
        {
            return dateTimeFormat.parseTimestamp(timestamp);
        }
        catch (ParseDateException ex)
        {
            throw createSAXParseException( ex.getMessage() );
        }
    }
  
    protected String getString(
        RaplaSAXAttributes atts,
        String key,
        String defaultString )
    {
        String str = atts.getValue( "", key );
        return (str != null) ? str : defaultString;
    }

    protected String getString( RaplaSAXAttributes atts, String key )
       	throws RaplaSAXParseException
    {
        String str = atts.getValue( "", key );
        if (str == null)
            throw createSAXParseException( "Attribute " + key + " not found!" );
        return str;
    }

    protected <T extends Entity> ReferenceInfo<T> getRef( RaplaSAXAttributes atts, String key, Class<T> clazz )
            throws RaplaSAXParseException
    {
        final String string = getString(atts, key);
        return new ReferenceInfo<T>(string, clazz);
    }


    /** return the new id */
    protected void setId( Entity entity, RaplaSAXAttributes atts )
    	throws RaplaSAXParseException
    {
        String idString = atts.getValue( "id" );
        ReferenceInfo id = getId( entity.getTypeClass(), idString );
        ((SimpleEntity)entity).setId(id);
    }

    /** return the new id */
    protected Object setNewId( Entity entity ) throws RaplaSAXParseException
    {
        try
        {
            String id = idTable.createId( entity.getTypeClass() );
            ((SimpleEntity)entity).setId( id );
            return id;
        }
        catch (RaplaException ex)
        {
            throw createSAXParseException( ex.getMessage() );
        }
    }

    protected <T extends SimpleEntity&Ownable> void setOwner( T ownable, RaplaSAXAttributes atts )
    	throws RaplaSAXParseException
    {
        String ownerString = atts.getValue( "owner" );
        if (ownerString != null)
        {
            ownable.putId("owner", getId( User.class, ownerString ) );
        }
        // No else case as no owner should still be possible and there should be no default owner 
    }
    
    protected void setLastChangedBy(SimpleEntity entity, RaplaSAXAttributes atts) {
		String lastChangedBy = atts.getValue( "last-changed-by");
		if ( lastChangedBy != null) 
		{
		    try 
		    {
		        User user = resolve(User.class,lastChangedBy );
		        entity.setLastChangedBy( user );
		    } 
		    catch (RaplaSAXParseException ex) 
		    {
		        getLogger().warn("Can't find user " + lastChangedBy + " in entity " + entity.getId());
		    }
		}
	}


    @SuppressWarnings("deprecation")
    protected ReferenceInfo getId( Class<? extends Entity> typeClass, String str ) throws RaplaSAXParseException
    {
        try
        {
            final String id;
            if ( str.equals(Category.SUPER_CATEGORY_REF.getId()))
            {
                return Category.SUPER_CATEGORY_REF;
            }
            else if (org.rapla.storage.OldIdMapping.isTextId(typeClass, str))
            {
               id = idTable.createId(typeClass, str);
            } 
            else
            {
                id = str;
            }
            return new ReferenceInfo(id, typeClass);
        }
        catch (RaplaException ex)
        {
            ex.printStackTrace();
            throw createSAXParseException( ex.getMessage() );
        }
    }

    void throwEntityNotFound( String type, Integer id ) throws RaplaSAXParseException
    {
        throw createSAXParseException( type + " with id '" + id + "' not found." );
    }

    public RaplaObject getType() throws RaplaSAXParseException
    {
        throw createSAXParseException( "Method getType() not implemented by subclass " + this.getClass().getName() );
    }

    /*
    protected CategoryImpl getSuperCategory()
    {
        return store.getSuperCategory();
    }
    */

    protected <T extends Entity> T resolve( Class<T> typeClass, String str ) throws RaplaSAXParseException
    {
        try
        {
            ReferenceInfo<T> id = getId( typeClass, str );
            T resolved = store.resolve( id );
			return resolved;
        }
        catch (EntityNotFoundException ex)
        {
            throw createSAXParseException(ex.getMessage() , ex);
        }
    }
    
    public void add(Entity entity) throws RaplaSAXParseException{
    	 if ( entity instanceof Classifiable)
         {
         	if ((( Classifiable) entity).getClassification() == null)
         	{
         		throw createSAXParseException("Classification can't be null");
         	}
         }
        store.put(entity);
        if ( entity instanceof  Category)
        {
            try
            {
                keyAndPathResolver.addCategory((Category)entity);
            }
            catch (EntityNotFoundException e)
            {
                throw new RaplaSAXParseException(e.getMessage(), e);
            }
        }
    }

    protected KeyAndPathResolver getKeyAndPathResolver()
    {
        return  keyAndPathResolver;
    }

    protected ReferenceInfo<Category> getGroup(String groupKey) throws RaplaSAXParseException{
        final String keyref = "category[key='" + groupKey + "']";
        return getGroupWithKeyRef(keyref);
    }

    protected ReferenceInfo<Category> getGroupWithKeyRef(String keyref) throws RaplaSAXParseException{
        final String keyrefParent = "category[key='"+Permission.GROUP_CATEGORY_KEY+"']";
        final String key = keyrefParent + "/" + keyref;
        final ReferenceInfo<Category> idForCategory = keyAndPathResolver.getIdForCategory(key);
        return idForCategory;
    }

    protected void putPassword( ReferenceInfo<User> userid, String password )
    {
        store.putPassword( userid, password);
    }

	protected void setCurrentTranslations(MultiLanguageName name) {
		String lang = i18n.getLang();
		boolean contains = name.getAvailableLanguages().contains( lang);
		if (!contains)
		{
			try
			{
				String translation = i18n.getString( name.getName("en"));
				name.setName( lang, translation);
			}
			catch (Exception ex)
			{
			}
		}
	}

	static public String wrapRaplaDataTag(String xml) {
		StringBuilder dataElement = new StringBuilder();
	    dataElement.append("<rapla:data ");
	    for (int i=0;i<RaplaXMLWriter.NAMESPACE_ARRAY.length;i++) {
	        String prefix = RaplaXMLWriter.NAMESPACE_ARRAY[i][1];
	        String uri = RaplaXMLWriter.NAMESPACE_ARRAY[i][0];
	        if ( prefix == null) {
	            dataElement.append("xmlns=");
	        } else {
	           dataElement.append("xmlns:" + prefix + "=");
	        }
	        dataElement.append("\"");
	        dataElement.append( uri );
	        dataElement.append("\" ");
	    }
	    dataElement.append(">");
	    dataElement.append( xml  );
	    dataElement.append( "</rapla:data>");
	    String xmlWithNamespaces = dataElement.toString();
		return xmlWithNamespaces;
	}

	public EntityStore getStore()
	{
	    return store;
	}
}
