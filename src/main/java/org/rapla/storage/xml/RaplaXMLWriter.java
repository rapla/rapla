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
package org.rapla.storage.xml;

import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.inject.Provider;

import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.components.util.xml.XMLWriter;
import org.rapla.entities.Annotatable;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.MultiLanguageName;
import org.rapla.entities.Ownable;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.Timestamp;
import org.rapla.entities.User;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.PermissionContainer;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;

/** Stores the data from the local cache in XML-format to a print-writer.*/
abstract public class RaplaXMLWriter extends XMLWriter
    implements Namespaces
{

    //protected NamespaceSupport namespaceSupport = new NamespaceSupport();
    private boolean isPrintId;

    private Map<String,Class<? extends RaplaObject>> localnameMap;
    Logger logger;
    Map<Class<? extends RaplaObject>,RaplaXMLWriter> writerMap;
    protected RaplaXMLContext context;
    protected SerializableDateTimeFormat dateTimeFormat = SerializableDateTimeFormat.INSTANCE;
    Provider<Category> superCategory;
    
    public RaplaXMLWriter( RaplaXMLContext context) throws RaplaException {
        this.context = context;
        enableLogging( context.lookup( Logger.class));
        this.writerMap =context.lookup( PreferenceWriter.WRITERMAP );
        this.localnameMap = context.lookup(PreferenceReader.LOCALNAMEMAPENTRY);
        this.isPrintId = context.has(IOContext.PRINTID);
        this.superCategory = context.lookup( IOContext.SUPERCATEGORY);

//        namespaceSupport.pushContext();
//        for (int i=0;i<NAMESPACE_ARRAY.length;i++) {
//            String prefix = NAMESPACE_ARRAY[i][1];
//            String uri = NAMESPACE_ARRAY[i][0];
//            if ( prefix != null) {
//                namespaceSupport.declarePrefix(prefix, uri);
//            }
//        }
    }
    
    public Category getSuperCategory()
    {
    	return superCategory.get();
    }

    public void enableLogging(Logger logger) {
        this.logger = logger;
    }

    protected Logger getLogger() {
        return logger;
    }

    protected void printTimestamp(Timestamp stamp) throws IOException {
        final Date createTime = stamp.getCreateDate();
        final Date lastChangeTime = stamp.getLastChanged();
        if ( createTime != null)
        {
            att("created-at", SerializableDateTimeFormat.INSTANCE.formatTimestamp( createTime));
		}
        if ( lastChangeTime != null)
        {
            att("last-changed", SerializableDateTimeFormat.INSTANCE.formatTimestamp( lastChangeTime));
        }
        ReferenceInfo<User> userId = stamp.getLastChangedBy();
        if ( userId != null)
        {  
            att("last-changed-by", userId);
        }
    }

    protected void att(String key, ReferenceInfo ref) throws IOException {
        att(key, ref.getId() );
    }


    protected void printTranslation(MultiLanguageName name) throws IOException {
        Iterator<String> it= name.getAvailableLanguages().iterator();
        while (it.hasNext()) {
            String lang = it.next();
            String value = name.getName(lang);
            openTag("doc:name");
            att("lang",lang);
            closeTagOnLine();
            printEncode(value);
            closeElementOnLine("doc:name");
            println();
        }
    }
    
    protected void printPermissions(PermissionContainer permissionContainer) throws IOException, RaplaException 
    {
        for ( Permission p : permissionContainer.getPermissionList() ){
            printPermission(p);
        }
    }

    protected void printPermission(Permission p) throws IOException,RaplaException {
        openTag("rapla:permission");
        if ( p.getUser() != null ) {
            att("user", getId( p.getUser() ));
        } else if ( p.getGroup() != null ) {
            att( "group", getGroupPath( p.getGroup() ) );
        }
        if ( p.getMinAdvance() != null ) {
            att ( "min-advance", p.getMinAdvance().toString() );
        }
        if ( p.getMaxAdvance() != null ) {
            att ( "max-advance", p.getMaxAdvance().toString() );
        }
        if ( p.getStart() != null ) {
            att ( "start-date", dateTimeFormat.formatDate(  p.getStart() ) );
        }
        if ( p.getEnd() != null ) {
            att ( "end-date", dateTimeFormat.formatDate(  p.getEnd() ) );
        }
        att("access", p.getAccessLevel().name().toLowerCase() );
        closeElementTag();
    }

    private String getGroupPath( Category category) throws EntityNotFoundException {
        Category rootCategory = getSuperCategory().getCategory(Permission.GROUP_CATEGORY_KEY);
        return ((CategoryImpl) rootCategory ).getPathForCategory(category);
    }


    protected void printAnnotations(Annotatable annotatable, boolean includeTags) throws IOException{
        String[] keys = annotatable.getAnnotationKeys();
        if ( keys.length == 0 )
            return;
        if ( includeTags)
        {        	
        	openElement("doc:annotations");
        }
        for (String key:keys) {
            String value = annotatable.getAnnotation(key);
            openTag("rapla:annotation");
            att("key", key);
            closeTagOnLine();
            printEncode(value);
            closeElementOnLine("rapla:annotation");
            println();
        }
        if ( includeTags)
        {
        	closeElement("doc:annotations");
        }
    }

    protected void printAnnotations(Annotatable annotatable) throws IOException{
    	printAnnotations(annotatable, true);
    }


    protected void printAttributeValue(Attribute attribute, Object value) throws IOException,RaplaException {
    	if ( value == null)
    	{
    		return;
    	}
    	AttributeType type = attribute.getType();
    	if (type.equals(AttributeType.ALLOCATABLE))
    	{
    	      print(getId((Entity)value));
    	}
    	else if (type.equals(AttributeType.CATEGORY))
        {
            CategoryImpl rootCategory = (CategoryImpl) attribute.getConstraint(ConstraintIds.KEY_ROOT_CATEGORY);
            if ( !(value instanceof Category))
            {
                throw new RaplaException("Wrong attribute value Category expected but was " + value.getClass());
            }
            Category categoryValue = (Category)value;
            if (rootCategory == null)
            {
                getLogger().error("root category missing for attriubte " + attribute);
            }
            else
            {
                String keyPathString = getKeyPath(rootCategory, categoryValue);
                print( keyPathString);
            }
        }
        else if (type.equals(AttributeType.DATE) )
        {
            final Date date;
            if  ( value instanceof Date) 
                date = (Date)value;
            else
                date = null;
            printEncode( dateTimeFormat.formatDate( date ) );
        }
        else
        {
            printEncode( value.toString() );
        }
    }

    private String getKeyPath(CategoryImpl rootCategory, Category categoryValue) throws EntityNotFoundException {
        List<String> pathForCategory= null;
        try
        {
            pathForCategory = rootCategory.getPathForCategory(categoryValue, true );
        } catch (EntityNotFoundException ex)
        {
            while ( rootCategory.getParent() != null)
            {
                rootCategory = (CategoryImpl) rootCategory.getParent();
                if ( rootCategory.isAncestorOf( rootCategory))
                {
                    throw new IllegalStateException("Illegal category circle detected!");
                }
            }
            pathForCategory = rootCategory.getPathForCategory(categoryValue, true );
        }
        String keyPathString = CategoryImpl.getKeyPathString(pathForCategory);
        return keyPathString;
    }


    protected void printOwner(Ownable obj) throws IOException {
        ReferenceInfo<User> userId = obj.getOwnerRef();
        if (userId == null)
            return;
        att("owner", userId);
    }


    protected void printReference(Entity entity) throws IOException, EntityNotFoundException {
        String localName = RaplaType.getLocalName(entity);
        openTag("rapla:" + localName);
        final Class typeClass = entity.getTypeClass();
        if ( typeClass == DynamicType.class ) {
            att("keyref", ((DynamicType)entity).getKey());
        }
        else if ( typeClass == Category.class  && !isPrintId())
        {
            String path = getKeyPath( (CategoryImpl)getSuperCategory(), (Category) entity);
            att("keyref", path);
        } 
        else 
        {
            att("idref",getId( entity));
        }
        closeElementTag();
    }


    protected String getId(Entity entity) {
        Comparable id2 = entity.getId();
        return id2.toString();
    }

    protected RaplaXMLWriter getWriterFor(Class<? extends RaplaObject> classType) throws RaplaException {
        RaplaXMLWriter writer = writerMap.get(classType);
        if ( writer == null) {
            throw new RaplaException("No writer for type " + classType);
        }
        writer.setIndentLevel( getIndentLevel());
        writer.setWriter( getWriter());
        return writer;
     }

    protected void printId(Entity entity) throws IOException {
        att("id", getId( entity ));
    }

    protected void printIdRef(Entity entity) throws IOException {
        att("idref", getId( entity ) );
    }

    /** Returns if the ids should be saved, even when keys are
     * available. */
    public boolean isPrintId() {
        return isPrintId;
    }

    /**
     * @throws IOException  
     */
    public void writeObject(@SuppressWarnings("unused") RaplaObject object) throws IOException, RaplaException {
        throw new RaplaException("Method not implemented by subclass " + this.getClass().getName());
    }

    /*
    public String getLocalNameForType(Class<? extends RaplaObject> raplaType) throws RaplaException{
        for (Iterator<Map.Entry<String,Class<? extends RaplaObject>>> it = localnameMap.entrySet().iterator();it.hasNext();) {
            Map.Entry<String,Class<? extends RaplaObject>> entry =it.next();
            if (entry.getValue().equals( raplaType)) {
                return entry.getKey();
            }
        }
        throw new RaplaException("No writer declared for Type " + raplaType );
    }
    */




}



