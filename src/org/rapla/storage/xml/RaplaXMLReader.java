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

import java.text.ParseException;
import java.util.Date;
import java.util.Map;

import org.rapla.components.util.SerializableDateTimeFormat;
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
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.RefEntity;
import org.rapla.facade.Conflict;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.storage.IdTable;
import org.rapla.storage.LocalCache;
import org.rapla.storage.impl.EntityStore;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class RaplaXMLReader extends DelegationHandler implements Namespaces
{
    EntityStore resolver;
    Logger logger;
    IdTable idTable;
    RaplaContext context;
    Map<String,RaplaType> localnameMap;
    Map<Object,RaplaXMLReader> readerMap;
    SerializableDateTimeFormat dateTimeFormat;
    private SerializableDateTimeFormat dateTimeFormatTimestamp;
    I18nBundle i18n;

    public RaplaXMLReader( RaplaContext context ) throws RaplaException
    {
        logger = context.lookup( Logger.class );
        this.context = context;
        this.i18n = context.lookup(RaplaComponent.RAPLA_RESOURCES);
        this.resolver = context.lookup( EntityStore.class); 
        this.idTable = context.lookup( IdTable.class );
        RaplaLocale raplaLocale = context.lookup( RaplaLocale.class );
        dateTimeFormat = new SerializableDateTimeFormat( raplaLocale.createCalendar() );
        dateTimeFormatTimestamp = new SerializableDateTimeFormat(raplaLocale.createCalendar());
        this.localnameMap = context.lookup( PreferenceReader.LOCALNAMEMAPENTRY );
        this.readerMap = context.lookup( PreferenceReader.READERMAP );
    }

    public RaplaType getTypeForLocalName( String localName )
        throws SAXParseException
    {
        RaplaType type =  localnameMap.get( localName );
        if (type == null)
            throw createSAXParseException( "No type declared for localname " + localName );
        return type;
    }

    /**
     * @param raplaType
     * @throws SAXParseException
     */
    protected RaplaXMLReader getChildHandlerForType( RaplaType raplaType )
        throws SAXParseException
    {
        RaplaXMLReader childReader = readerMap.get( raplaType );
        if (childReader == null)
        {
            throw createSAXParseException( "No Reader declared for type " + raplaType );
        }
        childReader.setDocumentLocator( getLocator() );
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
    
    protected boolean isContentCategoryId( String content )
    {
        if ( content == null)
        {
            return false;
        }
        content = content.trim();
        String KEY_START = Category.TYPE.getLocalName() + "_";
        boolean idContent = (content.indexOf( KEY_START ) == 0  && content.length() < KEY_START.length() + 10 && content.length() > 0);
        return idContent;
    }


    public Long parseLong( String text ) throws SAXException
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

    public Date parseDate( String date, boolean fillDate ) throws SAXException
    {
        try
        {
            return dateTimeFormat.parseDate( date, fillDate );
        }
        catch (ParseException ex)
        {
            throw createSAXParseException( ex.getMessage() );
        }
    }

    public Date parseDateTime( String date, String time ) throws SAXException
    {
        try
        {
            return dateTimeFormat.parseDateTime( date, time );
        }
        catch (ParseException ex)
        {
            throw createSAXParseException( ex.getMessage() );
        }
    }

    public Date parseTimestamp( String timestamp ) throws SAXException
    {
        try
        {
            return dateTimeFormatTimestamp.parseTimestamp(timestamp);
        }
        catch (ParseException ex)
        {
            throw createSAXParseException( ex.getMessage() );
        }
    }
  
    protected String getString(
        Attributes atts,
        String key,
        String defaultString )
    {
        String str = atts.getValue( "", key );
        return (str != null) ? str : defaultString;
    }

    protected String getString( Attributes atts, String key )
        throws SAXParseException
    {
        String str = atts.getValue( "", key );
        if (str == null)
            throw createSAXParseException( "Attribute " + key + " not found!" );
        return str;
    }

    /** return the new id */
    protected Object setId( RefEntity<?> entity, Attributes atts )
        throws SAXException
    {
        String idString = atts.getValue( "id" );
        Comparable id = getId( entity.getRaplaType(), idString );
        entity.setId( id );
        return id;
    }

    protected void setVersionIfThere( RefEntity<?> entity, Attributes atts )
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
    protected Object setNewId( RefEntity<?> entity ) throws SAXException
    {
        try
        {
            Comparable id = idTable.createId( entity.getRaplaType() );
            entity.setId( id );
            return id;
        }
        catch (RaplaException ex)
        {
            throw createSAXParseException( ex.getMessage() );
        }
    }

    protected void setOwner( Ownable ownable, Attributes atts )
        throws SAXException
    {
        String ownerString = atts.getValue( "owner" );
        if (ownerString != null)
        {
            ownable.setOwner( resolve( User.TYPE, ownerString ) );
        }
        // No else case as no owner should still be possible and there should be no default owner 
    }

    protected Comparable getId( RaplaType type, String str ) throws SAXException
    {
        try
        {
        	if ( type == Conflict.TYPE)
        	{
        		return str;
        	}
        	Comparable id = LocalCache.getId( type, str );
            return id;
        }
        catch (ParseException ex)
        {
            ex.printStackTrace();
            throw createSAXParseException( ex.getMessage() );
        }
    }

    void throwEntityNotFound( String type, Integer id ) throws SAXException
    {
        throw createSAXParseException( type + " with id '" + id + "' not found." );
    }

    public RaplaObject getType() throws SAXException
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

    protected <T extends RaplaObject> T resolve( RaplaType<T> type, String str ) throws SAXException
    {
        try
        {
            Comparable id = getId( type, str );
			RefEntity<?> resolved = resolver.resolve( id );
			@SuppressWarnings("unchecked")
			T casted = (T)resolved;
			return casted;
        }
        catch (EntityNotFoundException ex)
        {
            throw createSAXParseException( ex.getMessage() , ex);
        }
    }
    
    protected Object parseAttributeValue( Attribute attribute, String text ) throws SAXException
    {
        try
        {
            EntityResolver resolver = null;
            if (isContentCategoryId( text))
                resolver = this.resolver;
            return AttributeImpl.parseAttributeValue( attribute, text, resolver );
        }
        catch (ParseException ex)
        {
            throw createSAXParseException( ex.getMessage() );
        }
    }
    
    public void add(RefEntity<?> entity){
        resolver.put(entity);
    }
    
    public void remove(String localname, String id) throws SAXException
    {
        RaplaType type = getTypeForLocalName( localname);
        Object idObject = getId( type, id );
        resolver.addRemoveId( idObject );
    }
    
    public void reference(String localname, String id) throws SAXException
    {
        RaplaType type = getTypeForLocalName( localname);
        Object idObject = getId( type, id );
        resolver.addReferenceId( idObject );
    }
    
    public void store(String localname, String id) throws SAXException
    {
        RaplaType type = getTypeForLocalName( localname);
        Object idObject = getId( type, id );
        resolver.addStoreId( idObject );
    }
    
    protected Category getCategoryFromPath( String path ) throws SAXParseException 
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
    
    protected Category getGroup(String groupKey) throws SAXParseException{
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
            throw createSAXParseException( ex );
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


}
