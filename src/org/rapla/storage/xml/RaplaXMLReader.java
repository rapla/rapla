/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Christopher Kohlhaas                                  |
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

import org.rapla.components.util.ParseDateException;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.components.util.xml.RaplaSAXAttributes;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.Category;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.MultiLanguageName;
import org.rapla.entities.Ownable;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.AttributeImpl;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.storage.RefEntity;
import org.rapla.entities.storage.internal.SimpleEntity;
import org.rapla.facade.Conflict;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.storage.IdTable;
import org.rapla.storage.LocalCache;
import org.rapla.storage.impl.EntityStore;

public class RaplaXMLReader extends DelegationHandler implements Namespaces
{
    EntityStore resolver;
    Logger logger;
    IdTable idTable;
    RaplaContext context;
    Map<String,RaplaType> localnameMap;
    Map<Object,RaplaXMLReader> readerMap;
    SerializableDateTimeFormat dateTimeFormat;
    I18nBundle i18n;

    public RaplaXMLReader( RaplaContext context ) throws RaplaException
    {
        logger = context.lookup( Logger.class );
        this.context = context;
        this.i18n = context.lookup(RaplaComponent.RAPLA_RESOURCES);
        this.resolver = context.lookup( EntityStore.class); 
        this.idTable = context.lookup( IdTable.class );
        RaplaLocale raplaLocale = context.lookup( RaplaLocale.class );
        dateTimeFormat = raplaLocale.getSerializableFormat();
        this.localnameMap = context.lookup( PreferenceReader.LOCALNAMEMAPENTRY );
        this.readerMap = context.lookup( PreferenceReader.READERMAP );
    }

    public RaplaType getTypeForLocalName( String localName )
        throws RaplaSAXParseException
    {
        RaplaType type =  localnameMap.get( localName );
        if (type == null)
            throw createSAXParseException( "No type declared for localname " + localName );
        return type;
    }

    /**
     * @param raplaType
     * @throws RaplaSAXParseException
     */
    protected RaplaXMLReader getChildHandlerForType( RaplaType raplaType )
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

    /** return the new id */
    protected Object setId( RefEntity<?> entity, RaplaSAXAttributes atts )
    	throws RaplaSAXParseException
    {
        String idString = atts.getValue( "id" );
        String id = getId( entity.getRaplaType(), idString );
        entity.setId( id );
        return id;
    }

    protected void setVersionIfThere( RefEntity<?> entity, RaplaSAXAttributes atts )
    {
        String  version= atts.getValue( "version" );
        if ( version != null)
        {
            try {
                entity.setVersion( Long.parseLong( version));
            } 
            catch (NumberFormatException ex)
            {
                createSAXParseException( "Error parsing version-string '" + version + "'");
            }
        }
    }

    /** return the new id */
    protected Object setNewId( RefEntity<?> entity ) throws RaplaSAXParseException
    {
        try
        {
            String id = idTable.createId( entity.getRaplaType() );
            entity.setId( id );
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
            ownable.getReferenceHandler().putId("owner", getId( User.TYPE, ownerString ) );
        }
        // No else case as no owner should still be possible and there should be no default owner 
    }
    
    protected void setLastChangedBy(SimpleEntity entity, RaplaSAXAttributes atts) {
		String lastChangedBy = atts.getValue( "last-changed-by");
		if ( lastChangedBy != null) 
		{
		    try 
		    {
		        User user = resolve(User.TYPE,lastChangedBy );
		        entity.setLastChangedBy( user );
		    } 
		    catch (RaplaSAXParseException ex) 
		    {
		        getLogger().warn("Can't find user " + lastChangedBy + " in entity " + entity.getId());
		    }
		}
	}


    protected String getId( RaplaType type, String str ) throws RaplaSAXParseException
    {
        try
        {
        	if ( type == Conflict.TYPE)
        	{
        		return str;
        	}
        	String id = LocalCache.getId( type, str );
            return id;
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
    
    protected CategoryImpl getSuperCategory()
    {
        return resolver.getSuperCategory();
    }

    public DynamicType getDynamicType( String keyref )
    {
        return resolver.getDynamicType( keyref);
    }

    protected <T extends RaplaObject> T resolve( RaplaType<T> type, String str ) throws RaplaSAXParseException
    {
        try
        {
            String id = getId( type, str );
			RefEntity<?> resolved = resolver.resolve( id );
			@SuppressWarnings("unchecked")
			T casted = (T)resolved;
			return casted;
        }
        catch (EntityNotFoundException ex)
        {
            throw createSAXParseException(ex.getMessage() , ex);
        }
    }
    
    protected Object parseAttributeValue( Attribute attribute, String text ) throws RaplaSAXParseException
    {
        try
        {
            return AttributeImpl.parseAttributeValue( attribute, text);
        }
        catch (RaplaException ex)
        {
            throw createSAXParseException( ex.getMessage() );
        }
    }
    
    public void add(RefEntity<?> entity){
        resolver.put(entity);
    }
    
    public void remove(String localname, String id) throws RaplaSAXParseException
    {
        RaplaType type = getTypeForLocalName( localname);
        String idObject = getId( type, id );
        resolver.addRemoveId( idObject );
    }
    
    public void reference(String localname, String id) throws RaplaSAXParseException
    {
        RaplaType type = getTypeForLocalName( localname);
        String idObject = getId( type, id );
        resolver.addReferenceId( idObject );
    }
    
    public void store(String localname, String id) throws RaplaSAXParseException
    {
        RaplaType type = getTypeForLocalName( localname);
        String idObject = getId( type, id );
        resolver.addStoreId( idObject );
    }
    
    protected Category getCategoryFromPath( String path ) throws RaplaSAXParseException 
    {
        try
        {
            return getSuperCategory().getCategoryFromPath( path );
        }
        catch (Exception ex)
        {
            throw createSAXParseException( ex.getMessage() );
        }
    }
    
    protected Category getGroup(String groupKey) throws RaplaSAXParseException{
        CategoryImpl groupCategory = (CategoryImpl) getSuperCategory().getCategory(
            Permission.GROUP_CATEGORY_KEY );
        if (groupCategory == null)
        {
            throw createSAXParseException( Permission.GROUP_CATEGORY_KEY + " category not found" );
        }
        try
        {
            return groupCategory.getCategoryFromPath( groupKey );
        }
        catch (Exception ex)
        {
            throw createSAXParseException( ex.getMessage(),ex );
        }
    }
    
    
    protected void putPassword( Object userid, String password )
    {
        resolver.putPassword( userid, password);
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


}
