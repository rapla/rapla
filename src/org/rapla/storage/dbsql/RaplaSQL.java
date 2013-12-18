/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org .       |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, of which license fullfill the Open Source    |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.storage.dbsql;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.TreeMap;

import org.rapla.components.util.Assert;
import org.rapla.components.util.DateTools;
import org.rapla.entities.Annotatable;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.entities.domain.internal.PeriodImpl;
import org.rapla.entities.domain.internal.PermissionImpl;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeAnnotations;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.AttributeImpl;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.internal.UserImpl;
import org.rapla.entities.storage.RefEntity;
import org.rapla.entities.storage.internal.SimpleIdentifier;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.storage.xml.CategoryReader;
import org.rapla.storage.xml.PreferenceReader;
import org.rapla.storage.xml.RaplaNonValidatedInput;
import org.rapla.storage.xml.RaplaXMLReader;
import org.rapla.storage.xml.RaplaXMLWriter;
import org.xml.sax.SAXException;

class RaplaSQL {
    private final List<RaplaTypeStorage> stores = new ArrayList<RaplaTypeStorage>();
    private final Logger logger;
    
    RaplaSQL( RaplaContext context) throws RaplaException{
        logger =  context.lookup( Logger.class);
        // The order is important. e.g. appointments can only be loaded if the reservation they are refering to are already loaded.
	    stores.add(new CategoryStorage( context));
	    stores.add(new DynamicTypeStorage( context));
		stores.add(new UserStorage( context));
	    stores.add(new AllocatableStorage( context));
	    stores.add(new PreferenceStorage( context));
		stores.add(new PeriodStorage( context));
	    stores.add(new ReservationStorage( context));
		stores.add(new AppointmentStorage( context));
	}
    
    private List<Storage<?>> getStoresWithChildren() 
    {
    	List<Storage<?>> storages = new ArrayList<Storage<?>>();
    	for ( RaplaTypeStorage store:stores)
    	{
    		storages.add( store);
    		@SuppressWarnings("unchecked")
			Collection<Storage<?>> subStores = store.getSubStores();
			storages.addAll( subStores);
    	}
		return storages;
	}

    protected Logger getLogger() {
    	return logger;
    }

/***************************************************
 *   Create everything                             *
 ***************************************************/
    synchronized public void createAll(Connection con)
        throws SQLException,RaplaException
    {
		for (RaplaTypeStorage storage: stores) {
		    storage.setConnection(con);
		    try
		    {
		    	storage.insertAll();
		    }
            finally
            {
            	storage.setConnection( null);
            }
		}
    }

    synchronized public void removeAll(Connection con)
    throws SQLException
    {
        ListIterator<RaplaTypeStorage> listIt = stores.listIterator(stores.size());
        while (listIt.hasPrevious()) {
            Storage storage =  listIt.previous();
            storage.setConnection(con);
            try
            {
            	storage.deleteAll();
            }
            finally
            {
            	storage.setConnection( null);
            }
        }
    }

    synchronized public void loadAll(Connection con) throws SQLException,RaplaException {
		for (Storage storage:stores)
		{
		    storage.setConnection(con);
		    try
		    {
		    	storage.loadAll();
		    }
            finally
            {
            	storage.setConnection( null);
            }
		}
    }

    @SuppressWarnings("unchecked")
	synchronized public void remove(Connection con,RefEntity<?> entity) throws SQLException,RaplaException {
    	if ( Attribute.TYPE.equals(  entity.getRaplaType() ))
			return;
		for (RaplaTypeStorage storage:stores) {
		    if (storage.canStore(entity)) {
		    	storage.setConnection(con);
		    	try
		    	{
			    	List<RefEntity<?>> list = new ArrayList<RefEntity<?>>();
			    	list.add( entity);
	                storage.delete(list);
		    	}
	            finally
	            {
	            	storage.setConnection( null);
	            }
		    	return;
		    }
		}
		throw new RaplaException("No Storage-Sublass matches this object: " + entity.getClass());
    }

    @SuppressWarnings("unchecked")
	synchronized public void store(Connection con, Collection<RefEntity<?>> entities) throws SQLException,RaplaException {

        Map<Storage,List<RefEntity<?>>> store = new LinkedHashMap<Storage, List<RefEntity<?>>>();
        for ( RefEntity<?> entity:entities)
        {
            if ( Attribute.TYPE.equals(  entity.getRaplaType() ))
                continue;
            boolean found = false;
            for ( RaplaTypeStorage storage: stores)
            {
                if (storage.canStore(entity)) {
                    List<RefEntity<?>> list = store.get( storage);
                    if ( list == null)
                    {
                        list = new ArrayList<RefEntity<?>>();
                        store.put( storage, list);
                    }
                    list.add( entity);
                    found = true;
                }
            }
            if (!found)
            {
                throw new RaplaException("No Storage-Sublass matches this object: " + entity.getClass());
            }   
        }
        for ( Storage storage: store.keySet())
        {
            storage.setConnection(con);
            try
            {
            	List<RefEntity<?>> list = store.get( storage);
            	storage.save(list);
            }
            finally
            {
            	storage.setConnection( null);
            }
        }
    }

	public void createOrUpdateIfNecessary(Connection con,
			Map<String, TableDef> schema) throws SQLException, RaplaException {
		   // Upgrade db if necessary
    	for (Storage<?> storage:getStoresWithChildren())
		{
    		storage.setConnection(con);
    		try
    		{
    			storage.createOrUpdateIfNecessary( schema);
    		}
            finally
            {
            	storage.setConnection( null);
            }
		}
	}
}

abstract class RaplaTypeStorage<T extends Entity<T>> extends EntityStorage<T> {
	RaplaType raplaType;

	RaplaTypeStorage( RaplaContext context, RaplaType raplaType, String tableName, String[] entries) throws RaplaException {
		super( context,tableName, entries );
		this.raplaType = raplaType;
	}
    boolean canStore(RaplaObject entity) {
    	return entity.getRaplaType().is(raplaType);
    }
    void insertAll() throws SQLException,RaplaException {
        Collection<RefEntity<T>> collection = cache.getCollection( raplaType );
		insert(collection);
    }

    protected String getXML(RaplaObject type) throws RaplaException {
		RaplaXMLWriter dynamicTypeWriter = getWriterFor( type.getRaplaType());
		StringWriter stringWriter = new StringWriter();
	    BufferedWriter bufferedWriter = new BufferedWriter(stringWriter);
	    dynamicTypeWriter.setWriter( bufferedWriter );
	    dynamicTypeWriter.setSQL( true );
	    try {
	        dynamicTypeWriter.writeObject(type);
	        bufferedWriter.flush();
	    } catch (IOException ex) {
	        throw new RaplaException( ex);
	    }
	    return stringWriter.getBuffer().toString();

	}

	protected RaplaXMLReader processXML(RaplaType type, String xml) throws RaplaException {
	    RaplaXMLReader contentHandler = getReaderFor( type);
	    if ( xml== null ||  xml.trim().length() <= 10) {
	        throw new RaplaException("Can't load " + type);
	    }
	    try {
	        RaplaNonValidatedInput reader = getReader();
			reader.readWithNamespaces( xml, contentHandler);
	    } catch (IOException ex) {
	        throw new RaplaException( ex );
	    }
	    return contentHandler;
	}
}

class PeriodStorage extends RaplaTypeStorage<Period> {
    public PeriodStorage(RaplaContext context) throws RaplaException {
    	super(context, Period.TYPE,"PERIOD",new String[] {"ID INTEGER NOT NULL PRIMARY KEY","NAME VARCHAR(255) NOT NULL","PERIOD_START DATETIME NOT NULL","PERIOD_END DATETIME NOT NULL"});
    }

    protected int write(PreparedStatement stmt,RefEntity<Period> entity) throws SQLException {
		Period s =  entity.cast();
		setId(stmt,1,entity);
		setString(stmt,2,s.getName());
		setDate( stmt,3, s.getStart());
		setDate( stmt,4, s.getEnd());
		stmt.addBatch();
		return 1;
    }

    protected void load(ResultSet rset) throws SQLException {
		SimpleIdentifier id = new SimpleIdentifier(Period.TYPE, rset.getInt(1));
		String name = getString(rset,2, null);
		if ( name == null)
		{
			getLogger().warn("Name is null for " + id + ". Ignored");
		}
		java.util.Date von  = getDate(rset,3);
		java.util.Date bis  = getDate(rset,4);
		PeriodImpl period = new PeriodImpl(von,bis);
		period.setName( name );
		period.setId( id );
		put( period );
    }
}

class CategoryStorage extends RaplaTypeStorage<Category> {
	Map<Category,Integer> orderMap =  new HashMap<Category,Integer>();
    Map<Category,Object> categoriesWithoutParent = new TreeMap<Category,Object>(new Comparator<Category>()
        {
            public int compare( Category o1, Category o2 )
            {
                if ( o1.equals( o2))
                {
                    return 0;
                }
                int ordering1 = ( orderMap.get( o1 )).intValue();
                int ordering2 = (orderMap.get( o2 )).intValue();
                if ( ordering1 < ordering2)
                {
                    return -1;
                }
                if ( ordering1 > ordering2)
                {
                    return 1;
                }
                if (o1.hashCode() > o2.hashCode())
                {
                    return -1;
                }
                else
                {
                    return 1;
                }
            }
        
        }    
    );

    public CategoryStorage(RaplaContext context) throws RaplaException {
    	super(context,Category.TYPE, "CATEGORY",new String[] {"ID INTEGER NOT NULL PRIMARY KEY","PARENT_ID INTEGER KEY","CATEGORY_KEY VARCHAR(100) NOT NULL","LABEL VARCHAR(255)","DEFINITION TEXT NOT NULL","PARENT_ORDER INTEGER"});
    }
    
    @Override
	public void createOrUpdateIfNecessary(Map<String, TableDef> schema) throws SQLException, RaplaException
	{
    	super.createOrUpdateIfNecessary( schema);
    	checkAndAdd(schema, "DEFINITION");
    	checkAndAdd(schema, "PARENT_ORDER");
    	checkAndRetype(schema, "DEFINITION");
	}
        
	protected int write(PreparedStatement stmt,RefEntity<Category> entity) throws SQLException, RaplaException {
        Category root = getSuperCategory();
        if ( entity.equals( root ))
            return 0;
		Category category =  entity.cast();
		String name = category.getName( getLocale() );
		setId( stmt,1, entity);
		setId( stmt,2, category.getParent());
        int order = getOrder( category);
        String xml = getXML( category );
        setString(stmt,3, category.getKey());
		setString(stmt, 4, name );
		setText(stmt,5, xml);
        setInt( stmt,6, order);
		stmt.addBatch();
		return 1;
    }

    private int getOrder( Category category )
    {
        Category parent = category.getParent();
        if ( parent == null)
        {
            return 0;
        }
        Category[] childs = parent.getCategories();
        for ( int i=0;i<childs.length;i++)
        {
            if ( childs[i].equals( category))
            {
                return i;
            }
        }
        getLogger().error("Category not found in parent");
        return 0;
    }

    public RaplaXMLReader getReaderFor( RaplaType type) throws RaplaException {
        RaplaXMLReader reader = super.getReaderFor( type );
        if ( type.equals( Category.TYPE ) ) {
            ((CategoryReader) reader).setReadOnlyThisCategory( true);
        }
        return reader;
    }

    protected void load(ResultSet rset) throws SQLException, RaplaException {
    	Comparable id = readId(rset, 1, Category.class);
    	Comparable parentId = readId(rset, 2, Category.class, true);

        String xml = getText( rset, 5 );
    	Integer order = getInt(rset, 6 );
        CategoryImpl category;
    	if ( xml != null && xml.length() > 10 )
    	{
    	    category = ((CategoryReader)processXML( Category.TYPE, xml )).getCurrentCategory();
            //cache.remove( category );
    	}
    	else
    	{
    		String key = getString( rset, 3, null);
    		if ( key == null)
    		{
    			getLogger().warn("key is null for " + id + ". Ignored");
    		}
    		String name = getString( rset, 4, null );
    		if ( name == null)
    		{
    			getLogger().warn("Name is null for " + id + ". Ignored");
    		}
    		// for compatibility with version prior to 1.0rc1
    		category = new CategoryImpl();
	    	category.setKey( key );
	    	category.getName().setName( getLocale().getLanguage(), name);
    	}
		category.setId( id);
        put( category );

        orderMap.put( category, order);
        // parentId can also be null
        categoriesWithoutParent.put( category, parentId);
    }

    public void loadAll() throws RaplaException, SQLException {
    	categoriesWithoutParent.clear();
    	super.loadAll();
    	// then we rebuild the hirarchy
    	Iterator<Map.Entry<Category,Object>> it = categoriesWithoutParent.entrySet().iterator();
    	while (it.hasNext()) {
    		Map.Entry<Category,Object> entry = it.next();
    		Object parentId = entry.getValue();
    		Category category =  entry.getKey();
    		Category parent;
            Assert.notNull( category );
    		if ( parentId != null) {
    		    parent = (Category) resolve( parentId );
            } else {
    		    parent = getSuperCategory();
            }
            Assert.notNull( parent );
            parent.addCategory( category );
    	}
    }
}

class AllocatableStorage extends RaplaTypeStorage<Allocatable> {
    Map<Integer,Classification> classificationMap = new HashMap<Integer,Classification>();
    Map<Integer,Allocatable> allocatableMap = new HashMap<Integer,Allocatable>();
    AttributeValueStorage<Allocatable> resourceAttributeStorage;
    PermissionStorage permissionStorage;


    public AllocatableStorage(RaplaContext context ) throws RaplaException {
        super(context,Allocatable.TYPE,"RAPLA_RESOURCE",new String [] 
             	{"ID INTEGER NOT NULL PRIMARY KEY","TYPE_KEY VARCHAR(100) NOT NULL","IGNORE_CONFLICTS INTEGER NOT NULL","OWNER_ID INTEGER","CREATION_TIME TIMESTAMP","LAST_CHANGED TIMESTAMP","LAST_CHANGED_BY INTEGER DEFAULT NULL"});  
        resourceAttributeStorage = new AttributeValueStorage<Allocatable>(context,"RESOURCE_ATTRIBUTE_VALUE", "RESOURCE_ID",classificationMap, allocatableMap);
        permissionStorage = new PermissionStorage( context, allocatableMap);
        addSubStorage(resourceAttributeStorage);
        addSubStorage(permissionStorage );
    }

    @Override
    public void createOrUpdateIfNecessary( Map<String, TableDef> schema) throws SQLException, RaplaException
	{
    	checkRenameTable(  schema,"RESOURCE");
	    super.createOrUpdateIfNecessary( schema); 
	    checkAndAdd( schema, "OWNER_ID");
	    checkAndAdd( schema, "CREATION_TIME");
	    checkAndAdd( schema, "LAST_CHANGED");
	    checkAndAdd( schema, "LAST_CHANGED_BY");
    }
       
    
	protected int write(PreparedStatement stmt,RefEntity<Allocatable> entity) throws SQLException,RaplaException {
	  	AllocatableImpl allocatable = (AllocatableImpl) entity;
	  	String typeKey = allocatable.getClassification().getType().getElementKey();
		setId(stmt, 1, entity);
	  	setString(stmt,2, typeKey );
		setInt(stmt,3, allocatable.isHoldBackConflicts()? 1:0);
		org.rapla.entities.Timestamp timestamp = allocatable;
		setId(stmt,4, allocatable.getOwner() );
		setDate(stmt, 5,timestamp.getCreateTime() );
		setDate(stmt, 6,timestamp.getLastChangeTime() );
		setId( stmt,7,timestamp.getLastChangedBy() );
		stmt.addBatch();
      	return 1;
    }
	
    protected void load(ResultSet rset) throws SQLException, RaplaException {
        SimpleIdentifier id= readId(rset,1, Allocatable.class);
    	String typeKey = getString(rset,2 , null);
		boolean ignoreConflicts = getInt( rset, 3 ) == 1;
		final Date createDate = getDate( rset, 5);
		final Date lastChanged = getDate( rset, 6);
     	
    	AllocatableImpl allocatable = new AllocatableImpl(createDate, lastChanged);
    	allocatable.setId( id);
    	allocatable.setHoldBackConflicts( ignoreConflicts );
    	DynamicType type = null;
    	if ( typeKey != null)
    	{
    		type = getDynamicType(typeKey );
    	}
    	if ( type == null)
        {
            getLogger().error("Allocatable with id " + id + " has an unknown type " + typeKey + ". Try ignoring it");
            return;
        }
		allocatable.setOwner( resolveFromId(rset, 4, User.class) );
		allocatable.setLastChangedBy( resolveFromId(rset, 7, User.class) );
    	Classification classification = type.newClassification(false);
    	allocatable.setClassification( classification );
    	classificationMap.put( id.getKey(), classification );
    	allocatableMap.put(  id.getKey(), allocatable);
    	put( allocatable );
    }

	public void loadAll() throws RaplaException, SQLException {
    	classificationMap.clear();
    	super.loadAll();
    }
}

class ReservationStorage extends RaplaTypeStorage<Reservation> {
    Map<Integer,Classification> classificationMap = new HashMap<Integer,Classification>();
    Map<Integer,Reservation> reservationMap = new HashMap<Integer,Reservation>();
    AttributeValueStorage<Reservation> attributeValueStorage;
    public ReservationStorage(RaplaContext context) throws RaplaException {
        super(context,Reservation.TYPE, "EVENT",new String [] {"ID INTEGER NOT NULL PRIMARY KEY","TYPE_KEY VARCHAR(100) NOT NULL","OWNER_ID INTEGER NOT NULL","CREATION_TIME TIMESTAMP","LAST_CHANGED TIMESTAMP","LAST_CHANGED_BY INTEGER DEFAULT NULL"});
        attributeValueStorage = new AttributeValueStorage<Reservation>(context,"EVENT_ATTRIBUTE_VALUE","EVENT_ID", classificationMap, reservationMap);
        addSubStorage(attributeValueStorage);
    }
    
    @Override
    public void createOrUpdateIfNecessary( Map<String, TableDef> schema) throws SQLException, RaplaException
    {
    	super.createOrUpdateIfNecessary( schema);
    	checkAndAdd(schema, "LAST_CHANGED_BY");
    }

    protected int write(PreparedStatement stmt,RefEntity<Reservation> entity) throws SQLException,RaplaException {
      	Reservation event = entity.cast();
      	String typeKey = event.getClassification().getType().getElementKey();
      	setId(stmt,1, entity );
      	setString(stmt,2, typeKey );
    	setId(stmt,3, event.getOwner() );
    	org.rapla.entities.Timestamp timestamp = event;
        setDate( stmt,4,timestamp.getCreateTime());
        setDate( stmt,5,timestamp.getLastChangeTime());
        setId(stmt, 6, timestamp.getLastChangedBy());
        stmt.addBatch();
        return 1;
    }

	protected void load(ResultSet rset) throws SQLException, RaplaException {
    	final Date createDate = getDate(rset,4);
        final Date lastChanged = getDate(rset,5);
        ReservationImpl event = new ReservationImpl(createDate, lastChanged);
    	SimpleIdentifier id = readId(rset,1,Reservation.class);
		event.setId( id);
		String typeKey = getString(rset,2,null);
		DynamicType type = null;
    	if ( typeKey != null)
    	{
    		type = getDynamicType(typeKey );
    	}
    	if ( type == null)
        {
            getLogger().error("Reservation with id " + id + " has an unknown type " + typeKey + ". Try ignoring it");
            return;
        }
    	User user = resolveFromId(rset, 3, User.class);
    	if ( user == null )
        {
            return;
        }
        
    	event.setOwner( user );
        event.setLastChangedBy( resolveFromId(rset, 6, User.class) );
    	Classification classification = type.newClassification(false);
    	event.setClassification( classification );
    	classificationMap.put( id.getKey(), classification );
    	reservationMap.put( id.getKey(), event );
    	put( event );
    }

    public void loadAll() throws RaplaException, SQLException {
    	classificationMap.clear();
    	super.loadAll();
    }
}

/** This class should only be used within the ResourceStorage class*/
class AttributeValueStorage<T extends Classifiable & Annotatable & Entity<T> > extends EntityStorage<T> {
    Map<Integer,Classification> classificationMap;
    Map<Integer,? extends Annotatable> annotableMap;
    final String foreignKeyName;
    public AttributeValueStorage(RaplaContext context,String tablename, String foreignKeyName, Map<Integer,Classification> classificationMap, Map<Integer, ? extends Annotatable> annotableMap) throws RaplaException {
    	super(context, tablename, new String[]{foreignKeyName + " INTEGER NOT NULL KEY","ATTRIBUTE_KEY VARCHAR(100)","ATTRIBUTE_VALUE VARCHAR(20000)"});
        this.foreignKeyName = foreignKeyName;
        this.classificationMap = classificationMap;
        this.annotableMap = annotableMap;
    }

    @Override
    public void createOrUpdateIfNecessary( Map<String, TableDef> schema) throws SQLException, RaplaException
    {
    	super.createOrUpdateIfNecessary( schema);
    	checkAndRename( schema, "VALUE", "ATTRIBUTE_VALUE");
    	checkAndRetype(schema, "ATTRIBUTE_VALUE");
    }
    
    
	protected int write(PreparedStatement stmt,RefEntity<T> classifiable) throws EntityNotFoundException, SQLException {
        Classification classification =  classifiable.cast().getClassification();
        Attribute[] attributes = classification.getAttributes();
        int count =0;
        for (int i=0;i<attributes.length;i++) {
            Attribute attribute = attributes[i];
            Collection<Object> values = classification.getValues( attribute );
            for (Object value: values)
            {
            	String valueAsString;
            	if ( value instanceof Category)
            	{
            		@SuppressWarnings("unchecked")
                    RefEntity<Category> casted = (RefEntity<Category>) value;
                    valueAsString = ""+	getId(casted);
            	}
            	else
            	{
            		valueAsString = AttributeImpl.attributeValueToString( attribute, value, true);
            	}
		        setId(stmt,1, classifiable);
		        setString(stmt,2, attribute.getKey());
		        setString(stmt,3, valueAsString);
		        stmt.addBatch();
	            count++;
            }
        }
    	Annotatable annotatable = classifiable.cast();
    	for ( String key: annotatable.getAnnotationKeys())
    	{
    		String valueAsString = annotatable.getAnnotation( key);
    		setId(stmt,1, classifiable);
	        setString(stmt,2, annotationPrefix + key);
	     	setString(stmt,3, valueAsString);
	     	stmt.addBatch();
            count++;
    	}
        return count;
    }

    String annotationPrefix = "annotation:";
	
    protected void load(ResultSet rset) throws SQLException, RaplaException {
        int classifiableIdInt = rset.getInt( 1);
        String attributekey = rset.getString( 2 );
        if ( attributekey.startsWith(annotationPrefix))
        {
        	String annotationKey = attributekey.substring( annotationPrefix.length());
        	Annotatable annotatable = annotableMap.get(new Integer(classifiableIdInt));
        	if (annotatable != null)
        	{
	        	String valueAsString = rset.getString( 3);
	    	    if ( rset.wasNull() || valueAsString == null)
	    	    {
	    	    	annotatable.setAnnotation(annotationKey, null);
	    	    }
	    	    else
	    	    {
	    	    	annotatable.setAnnotation(annotationKey, valueAsString);
	    	    }
        	}
        	else
        	{
                getLogger().warn("No resource or reservation found for the id" + classifiableIdInt  + " ignoring.");
        	}
        }
        else
        {
            Classification classification = classificationMap.get(new Integer(classifiableIdInt));
            if ( classification == null) {
                getLogger().warn("No resource or reservation found for the id" + classifiableIdInt  + " ignoring.");
                return;
            }
	    	Attribute attribute = classification.getType().getAttribute( attributekey );
	    	if ( attribute == null) {
	    		getLogger().error("DynamicType '" +classification.getType() +"' doesnt have an attribute with the key " + attributekey + " Current allocatable/reservation Id " + classifiableIdInt + ". Ignoring attribute.");
	    		return;
	    	}
	    	String valueAsString = rset.getString( 3);
		    Object value = null;
	    	try {
	    	    if ( valueAsString != null )
	            {
	    	    	value = AttributeImpl.parseAttributeValue(attribute,valueAsString, getResolver()) ;
	             	String multiSelect = attribute.getAnnotation(AttributeAnnotations.KEY_MULTI_SELECT);
	             	if ( multiSelect != null && Boolean.valueOf(multiSelect))
	 				{
	 					Collection<Object> values = classification.getValues(attribute);
	 					Collection<Object> newCollection = new ArrayList<Object>(values);
	 					newCollection.add( value);
	 					classification.setValues(attribute, newCollection);
	                 }
	 				else
	 				{
	 					classification.setValue(attribute, value);
	 				}
	            }
	    	} catch (ParseException ex) {
	    	    throw new RaplaException( "Error parsing attribute value: " +ex.getMessage(), ex );
	    	}
        }
    }


}

class PermissionStorage extends EntityStorage<Allocatable>  {
    Map<Integer,Allocatable> allocatableMap;
    public PermissionStorage(RaplaContext context,Map<Integer,Allocatable> allocatableMap) throws RaplaException {
        super(context,"PERMISSION",new String[] {"RESOURCE_ID INTEGER NOT NULL KEY","USER_ID INTEGER","GROUP_ID INTEGER","ACCESS_LEVEL INTEGER NOT NULL","MIN_ADVANCE INTEGER","MAX_ADVANCE INTEGER","START_DATE DATETIME","END_DATE DATETIME"});
        this.allocatableMap = allocatableMap;
    }

    protected int write(PreparedStatement stmt, RefEntity<Allocatable> allocatable) throws SQLException, RaplaException {
        int count = 0;
        for (Permission s:allocatable.cast().getPermissions()) {
			setId(stmt,1,allocatable);
			setId(stmt,2,s.getUser());
			setId(stmt,3,s.getGroup());
			setInt(stmt,4, s.getAccessLevel());
			setInt( stmt,5, s.getMinAdvance());
			setInt( stmt,6, s.getMaxAdvance());
			setDate(stmt,7, s.getStart());
			setDate(stmt,8, s.getEnd());
			stmt.addBatch();
			count ++;
        }
        return count;
    }

    protected void load(ResultSet rset) throws SQLException, RaplaException {
        int allocatableIdInt = rset.getInt( 1);
        Allocatable allocatable = allocatableMap.get(new Integer(allocatableIdInt));
        if ( allocatable == null)
        {
        	getLogger().warn("Could not find resource object with id "+ allocatableIdInt + " for permission. Maybe the resource was deleted from the database.");
        	return;
        }
        PermissionImpl permission = new PermissionImpl();
        permission.setUser( resolveFromId(rset, 2, User.class));
        permission.setGroup( resolveFromId(rset, 3, Category.class));
        Integer accessLevel = getInt( rset, 4);
        // We multiply the access levels to add a more access levels between.
        if  ( accessLevel !=null)
        {
	        if ( accessLevel < 5)
	        {
	        	accessLevel *= 100;
	        }
	        permission.setAccessLevel( accessLevel );
        }
        permission.setMinAdvance( getInt(rset,5));
        permission.setMaxAdvance( getInt(rset,6));
        permission.setStart(getDate(rset, 7));
        permission.setEnd(getDate(rset, 8));
        // We need to add the permission at the end to ensure its unique. Permissions are stored in a set and duplicates are removed during the add method 
        allocatable.addPermission( permission );
    }

}


class AppointmentStorage extends RaplaTypeStorage<Appointment> {
    AppointmentExceptionStorage appointmentExceptionStorage;
    AllocationStorage allocationStorage;
    public AppointmentStorage(RaplaContext context) throws RaplaException {
        super(context, Appointment.TYPE,"APPOINTMENT",new String [] {"ID INTEGER NOT NULL PRIMARY KEY","EVENT_ID INTEGER NOT NULL KEY","APPOINTMENT_START DATETIME NOT NULL","APPOINTMENT_END DATETIME NOT NULL","REPETITION_TYPE VARCHAR(255)","REPETITION_NUMBER INTEGER","REPETITION_END DATETIME","REPETITION_INTERVAL INTEGER"});
        appointmentExceptionStorage = new AppointmentExceptionStorage(context);
        allocationStorage = new AllocationStorage( context);
        addSubStorage(appointmentExceptionStorage);
        addSubStorage(allocationStorage);
    }

    protected int write(PreparedStatement stmt,RefEntity<Appointment> entity) throws SQLException,RaplaException {
        Appointment appointment =  entity.cast();
      	setId( stmt, 1, entity);
      	setId( stmt, 2, appointment.getReservation());
      	setDate(stmt, 3, appointment.getStart());
      	setDate(stmt, 4, appointment.getEnd());
      	Repeating repeating = appointment.getRepeating();
      	if ( repeating == null) {
      		setString( stmt,5, null);
			setInt( stmt,6, null);
			setDate( stmt,7, null);
			setInt( stmt,8, null);
      	} else {
      		setString( stmt,5, repeating.getType().toString());
      	    int number = repeating.getNumber();
      	    setInt(stmt, 6, number >= 0 ? number : null);
      	    setDate(stmt, 7, repeating.getEnd());
      	    setInt(stmt,8, repeating.getInterval());
      	}
      	stmt.addBatch();
      	return 1;
    }

   

	protected void load(ResultSet rset) throws SQLException, RaplaException {
        SimpleIdentifier idInt = readId(rset, 1, Appointment.class);
        Reservation event = resolveFromId(rset, 2, Reservation.class);
        if ( event == null)
        {
        	return;
        }
        Date start = getDate(rset,3);
        Date end = getDate(rset,4);
        boolean wholeDayAppointment = start.getTime() == DateTools.cutDate( start.getTime()) && end.getTime() == DateTools.cutDate( end.getTime());
    	AppointmentImpl appointment = new AppointmentImpl(start, end);
    	appointment.setId( idInt);
    	appointment.setWholeDays( wholeDayAppointment);
    	event.addAppointment( appointment );
    	String repeatingType = getString( rset,5, null);
    	if ( repeatingType != null ) {
    	    appointment.setRepeatingEnabled( true );
    	    Repeating repeating = appointment.getRepeating();
    	    repeating.setType( RepeatingType.findForString( repeatingType ) );
    	    Date repeatingEnd = getDate(rset, 7);
	        if ( repeatingEnd != null ) {
	            repeating.setEnd( repeatingEnd);
	        } else {
	        	Integer number  = getInt( rset, 6);
	        	if ( number != null) {
	        		repeating.setNumber( number);
	        	} else {
	                repeating.setEnd( null );
	        	}
	        }

	        Integer interval = getInt( rset,8);
    	    if ( interval != null)
    	        repeating.setInterval( interval);
    	}
    	put( appointment );
    }
}


class AllocationStorage extends EntityStorage<Appointment>  {

    public AllocationStorage(RaplaContext context) throws RaplaException  
    {
        super(context,"ALLOCATION",new String [] {"APPOINTMENT_ID INTEGER NOT NULL KEY", "RESOURCE_ID INTEGER NOT NULL", "OPTIONAL INTEGER"});
    }
    
    @Override
    public void createOrUpdateIfNecessary(
    		Map<String, TableDef> schema) throws SQLException, RaplaException {
    	super.createOrUpdateIfNecessary( schema);
    	checkAndAdd( schema, "OPTIONAL");
    }
    
    protected int write(PreparedStatement stmt, RefEntity<Appointment> entity) throws SQLException, RaplaException {
        Appointment appointment =  entity.cast();
        Reservation event = appointment.getReservation();
        int count = 0;
        for (Allocatable allocatable: event.getAllocatablesFor(appointment)) {
    		setId(stmt,1, appointment);
    		setId(stmt,2, allocatable);
            stmt.setObject(3, null);
    		stmt.addBatch();
    		count++;
        }
        return count;
    }
  
    protected void load(ResultSet rset) throws SQLException, RaplaException {
    	Appointment appointment =resolveFromId(rset,1, Appointment.class);
    	if ( appointment == null)
    	{
    		return;
    	}
        Reservation event = appointment.getReservation();
        Allocatable allocatable = resolveFromId(rset, 2, Allocatable.class);
        if ( allocatable == null)
        {
        	return;
        }
        if ( !event.hasAllocated( allocatable ) ) {
            event.addAllocatable( allocatable );
        }
        Appointment[] appointments = event.getRestriction( allocatable );
        Appointment[] newAppointments = new Appointment[ appointments.length+ 1];
        System.arraycopy(appointments,0, newAppointments, 0, appointments.length );
        newAppointments[ appointments.length] = appointment;
        if (event.getAppointments().length > newAppointments.length ) {
            event.setRestriction( allocatable, newAppointments );
        } else {
            event.setRestriction( allocatable, new Appointment[] {} );
        }
    }



 }

class AppointmentExceptionStorage extends EntityStorage<Appointment>  {
    public AppointmentExceptionStorage(RaplaContext context) throws RaplaException {
        super(context,"APPOINTMENT_EXCEPTION",new String [] {"APPOINTMENT_ID INTEGER NOT NULL KEY","EXCEPTION_DATE DATETIME NOT NULL"});
    }


    protected int write(PreparedStatement stmt, RefEntity<Appointment> entity) throws SQLException, RaplaException {
        Repeating repeating = entity.cast().getRepeating();
        int count = 0;
        if ( repeating == null) {
            return count;
        }
	    for (Date exception: repeating.getExceptions()) {
	        setId( stmt, 1, entity );
	        setDate( stmt,2, exception);
	        stmt.addBatch();
	        count++;
        }
        return count;
	}

    protected void load(ResultSet rset) throws SQLException, RaplaException {
        Appointment appointment = resolveFromId( rset, 1, Appointment.class);
        if ( appointment == null)
        {
        	return;
        }
        Repeating repeating = appointment.getRepeating();
        if ( repeating != null) {
            Date date = getDate(rset,2 );
            repeating.addException( date );
        }
    }

}

class DynamicTypeStorage extends RaplaTypeStorage<DynamicType> {

    public DynamicTypeStorage(RaplaContext context) throws RaplaException {
        super(context, DynamicType.TYPE,"DYNAMIC_TYPE", new String [] {"ID INTEGER NOT NULL PRIMARY KEY","TYPE_KEY VARCHAR(100) NOT NULL","DEFINITION TEXT NOT NULL"});
    }

    @Override
    public void createOrUpdateIfNecessary(Map<String, TableDef> schema)
    		throws SQLException, RaplaException {
    	super.createOrUpdateIfNecessary(schema);
    	checkAndRetype(schema, "DEFINITION");
    }

	protected int write(PreparedStatement stmt,RefEntity<DynamicType> entity) throws SQLException, RaplaException {
        setId(stmt,1,entity);
        DynamicType type = entity.cast();
        setString(stmt,2, type.getElementKey());
        setText(stmt,3,  getXML( type) );
        stmt.addBatch();
        return 1;
    }

	protected void load(ResultSet rset) throws SQLException,RaplaException {
    	String xml = getText(rset,3);
    	processXML( DynamicType.TYPE, xml );
	}

}


class PreferenceStorage extends RaplaTypeStorage<Preferences> {

    public PreferenceStorage(RaplaContext context) throws RaplaException {
        super(context,Preferences.TYPE,"PREFERENCE",
	    new String [] {"USER_ID INTEGER KEY","ROLE VARCHAR(255) NOT NULL","STRING_VALUE VARCHAR(10000)","XML_VALUE TEXT"});
    }
    
    @Override
    public void createOrUpdateIfNecessary(Map<String, TableDef> schema)
    		throws SQLException, RaplaException {
    	super.createOrUpdateIfNecessary(schema);
    	checkAndRetype(schema, "STRING_VALUE");
    	checkAndRetype(schema, "XML_VALUE");
    }

    protected int write(PreparedStatement stmt, RefEntity<Preferences> entity) throws SQLException, RaplaException {
        PreferencesImpl preferences = (PreferencesImpl) entity;
        User user = preferences.getOwner();
        if ( user == null) {
			stmt.setInt(1, 0);
        } else {
            stmt.setInt(1,getId( (RefEntity<?>) user));
        }
        int count = 0;
        Iterator<String> it = preferences.getPreferenceEntries();
        while (it.hasNext()) {
            String role =  it.next();
            Object entry = preferences.getEntry(role);
            setString( stmt,2, role);
            String xml;
            String entryString;
            if ( entry instanceof String) {
                entryString = (String) entry;
            	xml = null;
            } else {
            	//System.out.println("Role " + role + " CHILDREN " + conf.getChildren().length);
                entryString = null;
            	xml = getXML( (RaplaObject)entry);
            }
            setString( stmt,3, entryString);

			setText(stmt, 4, xml);
       
            stmt.addBatch();
            count++;
        }
       
        return count;
    }

    protected void load(ResultSet rset) throws SQLException, RaplaException {
    	//findPreferences
    	//check if value set
    	//  yes read value
    	//  no read xml

        SimpleIdentifier userId = readId(rset, 1,User.class, true);
        User owner = null;
        Comparable preferenceId;
        if ( userId != null){
            owner = (User) get( userId );
            preferenceId = new SimpleIdentifier( Preferences.TYPE, userId.getKey() );
        } else {
        	preferenceId = new SimpleIdentifier( Preferences.TYPE, 0 );
        }
        PreferencesImpl preferences = (PreferencesImpl) get( preferenceId );
        if ( preferences == null) {
        	preferences = new PreferencesImpl();
        	preferences.setId(preferenceId);
        	preferences.setOwner(owner);
        	put( preferences );
        }
        String configRole = getString( rset, 2, null);
        if ( configRole == null)
        {
        	getLogger().warn("Configuration role for " + preferenceId + " is null. Ignoring entry.");
        }
        String value = getString( rset,3, null);
        if ( value!= null) {
            preferences.putEntry(configRole, value);
        } else {
        	String xml = getText(rset, 4);
	        if ( xml != null && xml.length() > 0)
	        {
		        PreferenceReader contentHandler = (PreferenceReader) processXML( Preferences.TYPE, xml );
		        try {
		            RaplaObject type = contentHandler.getChildType();
		            preferences.putEntry(configRole, type);
		        } catch (SAXException ex) {
		            throw new RaplaException (ex);
		        }
	        }
        }
    }

    @Override
    public void delete( Collection<RefEntity<Preferences>> entities) throws SQLException {
        PreparedStatement stmt = null;
        boolean deleteNullUserPreference = false;
        try {
            stmt = con.prepareStatement(deleteSql);
            for ( RefEntity<Preferences> entity: entities)
            {
                Preferences preferences = entity.cast();
                User user = preferences.getOwner();
                if ( user == null) {
                	deleteNullUserPreference = true;
                }
            	setId( stmt,1, user);
                stmt.addBatch();
            }
            if ( entities.size() > 0)
            {
                stmt.executeBatch();
            } 
        } finally {
            if (stmt!=null)
                stmt.close();
        }
        if ( deleteNullUserPreference )
        {
            PreparedStatement deleteNullStmt = con.prepareStatement("DELETE FROM " + tableName + " WHERE USER_ID IS NULL OR USER_ID=0");
            deleteNullStmt.execute();
        }
    }

 }

class UserStorage extends RaplaTypeStorage<User> {
    UserGroupStorage groupStorage;
    public UserStorage(RaplaContext context) throws RaplaException {
        super( context,User.TYPE, "RAPLA_USER",
	    new String [] {"ID INTEGER NOT NULL PRIMARY KEY","USERNAME VARCHAR(100) NOT NULL","PASSWORD VARCHAR(100)","NAME VARCHAR(255) NOT NULL","EMAIL VARCHAR(255) NOT NULL","ISADMIN INTEGER NOT NULL"});
        groupStorage = new UserGroupStorage( context );
        addSubStorage( groupStorage );
    }

    protected int write(PreparedStatement stmt,RefEntity<User> entity) throws SQLException, RaplaException {
       setId(stmt, 1, entity);
       User user =  entity.cast();
       stmt.setString(2,user.getUsername());
       String password = cache.getPassword(entity.getId());
       stmt.setString(3,password);
       stmt.setString(4,user.getName());
       stmt.setString(5,user.getEmail());
       stmt.setInt(6,user.isAdmin()?1:0);
       stmt.addBatch();
       return 1;
    }

    protected void load(ResultSet rset) throws SQLException, RaplaException {
        Comparable userId = readId(rset,1, User.class );
        String username = getString(rset,2, null);
        if ( username == null)
        {
        	getLogger().warn("Username is null for " + userId + " Ignoring user.");
        }
        String name = getString(rset,4,"");
        String email = getString(rset,5,"");
        boolean isAdmin = rset.getInt(6) == 1;
        UserImpl user = new UserImpl();
        user.setId( userId );
        user.setUsername( username );
        user.setName( name );
        user.setEmail( email );
        user.setAdmin( isAdmin );
        String password = getString(rset,3, null);
        if ( password != null) {
            putPassword(userId,password);
        }
        put(user);
   }
}

class UserGroupStorage extends EntityStorage<User> {
    public UserGroupStorage(RaplaContext context) throws RaplaException {
        super(context,"RAPLA_USER_GROUP", new String [] {"USER_ID INTEGER NOT NULL KEY","CATEGORY_ID INTEGER NOT NULL"});
    }

    protected int write(PreparedStatement stmt, RefEntity<User> entity) throws SQLException, RaplaException {
        setId( stmt,1, entity);
        int count = 0;
        for (Category category:entity.cast().getGroups()) {
            setId(stmt, 2, category);
            stmt.addBatch();
            count++;
        }
        return count;
    }

    protected void load(ResultSet rset) throws SQLException, RaplaException {
        User user = resolveFromId(rset, 1,User.class);
        if ( user == null)
        {
        	return;
        }
        Category category = resolveFromId(rset, 2,  Category.class);
        if ( category == null)
        {
        	return;
        }
        user.addGroup( category);
    }
}


