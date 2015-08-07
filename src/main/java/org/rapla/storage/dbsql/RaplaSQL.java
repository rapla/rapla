/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.rapla.components.util.Assert;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.xml.RaplaNonValidatedInput;
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
import org.rapla.entities.domain.EntityPermissionContainer;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Permission.AccessLevel;
import org.rapla.entities.domain.PermissionContainer;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.entities.domain.internal.PermissionImpl;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.AttributeImpl;
import org.rapla.entities.dynamictype.internal.ClassificationImpl;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.internal.UserImpl;
import org.rapla.facade.Conflict;
import org.rapla.facade.internal.ConflictImpl;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.storage.PreferencePatch;
import org.rapla.storage.xml.CategoryReader;
import org.rapla.storage.xml.PreferenceReader;
import org.rapla.storage.xml.RaplaXMLReader;
import org.rapla.storage.xml.RaplaXMLWriter;

class RaplaSQL {
    private final List<RaplaTypeStorage> stores = new ArrayList<RaplaTypeStorage>();
    private final Logger logger;
    RaplaContext context;
    PreferenceStorage preferencesStorage;
    
    RaplaSQL( RaplaContext context) throws RaplaException{
    	this.context = context;
        logger =  context.lookup( Logger.class);
        // The order is important. e.g. appointments can only be loaded if the reservation they are refering to are already loaded.
	    stores.add(new CategoryStorage( context));
	    stores.add(new UserStorage( context));
	    stores.add(new DynamicTypeStorage( context));
	    stores.add(new AllocatableStorage( context));
	    preferencesStorage = new PreferenceStorage( context);
        stores.add(preferencesStorage);
	    ReservationStorage reservationStorage = new ReservationStorage( context);
		stores.add(reservationStorage);
	    AppointmentStorage appointmentStorage = new AppointmentStorage( context);
		stores.add(appointmentStorage);
		stores.add(new ConflictStorage( context));
		// now set delegate because reservation storage should also use appointment storage
		reservationStorage.setAppointmentStorage( appointmentStorage);
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
		for (RaplaTypeStorage storage: stores) {
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
	synchronized public void remove(Connection con,Entity entity) throws SQLException,RaplaException {
    	if ( Attribute.TYPE ==  entity.getRaplaType() )
			return;
		for (RaplaTypeStorage storage:stores) {
		    if (storage.canStore(entity)) {
		    	storage.setConnection(con);
		    	try
		    	{
			    	List<Entity>list = new ArrayList<Entity>();
			    	list.add( entity);
	                storage.deleteEntities(list);
		    	}
		    	finally
		    	{
		    		storage.setConnection(null);
		    	}
		    	return;
		    }
		}
		throw new RaplaException("No Storage-Sublass matches this object: " + entity.getClass());
    }

    @SuppressWarnings("unchecked")
	synchronized public void store(Connection con, Collection<Entity>entities) throws SQLException,RaplaException {

        Map<Storage,List<Entity>> store = new LinkedHashMap<Storage, List<Entity>>();
        for ( Entity entity:entities)
        {
            if ( Attribute.TYPE ==  entity.getRaplaType() )
                continue;
            boolean found = false;
            for ( RaplaTypeStorage storage: stores)
            {
                if (storage.canStore(entity)) {
                    List<Entity>list = store.get( storage);
                    if ( list == null)
                    {
                        list = new ArrayList<Entity>();
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
            	List<Entity>list = store.get( storage);
            	storage.save(list);
            }
            finally
            {
            	storage.setConnection( null);
            }
        }
    }

	public void createOrUpdateIfNecessary(Connection con, Map<String, TableDef> schema) throws SQLException, RaplaException {
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

    public void storePatches(Connection connection, List<PreferencePatch> preferencePatches) throws SQLException, RaplaException {
        PreferenceStorage storage = preferencesStorage;
        storage.setConnection( connection);
        try
        {
            preferencesStorage.storePatches( preferencePatches);
        }
        finally
        {
            storage.setConnection( null);
        }
    }


}

abstract class RaplaTypeStorage<T extends Entity<T>> extends EntityStorage<T> {
	RaplaType raplaType;

	RaplaTypeStorage( RaplaContext context, RaplaType raplaType, String tableName, String[] entries) throws RaplaException {
		super( context,tableName, entries );
		this.raplaType = raplaType;
	}
    boolean canStore(Entity entity) {
    	return entity.getRaplaType() == raplaType;
    }
    
    abstract void insertAll() throws SQLException,RaplaException;

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
	    RaplaXMLReader reader = getReaderFor( type);
	    if ( xml== null ||  xml.trim().length() <= 10) {
	        throw new RaplaException("Can't load " + type);
	    }
	    String xmlWithNamespaces = RaplaXMLReader.wrapRaplaDataTag(xml); 
	    RaplaNonValidatedInput parser = getReader();
		parser.read(xmlWithNamespaces, reader, logger);
	    return reader;
	}
}

class CategoryStorage extends RaplaTypeStorage<Category> {
	Map<Category,Integer> orderMap =  new HashMap<Category,Integer>();
    Map<Category,String> categoriesWithoutParent = new TreeMap<Category,String>(new Comparator<Category>()
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
    	super(context,Category.TYPE, "CATEGORY",new String[] {"ID VARCHAR(255) NOT NULL PRIMARY KEY","PARENT_ID VARCHAR(255) KEY","CATEGORY_KEY VARCHAR(255) NOT NULL","DEFINITION TEXT NOT NULL","PARENT_ORDER INTEGER"});
    }
    
    @Override
    public void deleteEntities(Iterable<Category> entities) throws SQLException,RaplaException {
    	Set<String> idList = new HashSet<String>();
    	for ( Category cat:entities)
    	{
    		idList.addAll( getIds( cat.getId()));
    	}
    	deleteIds(idList);
    }
    
    @Override
    public void insert(Iterable<Category> entities) throws SQLException, RaplaException {
        Set<Category> transitiveCategories = new LinkedHashSet<Category>();
        for ( Category cat: entities)
        {
            transitiveCategories.add( cat);
            transitiveCategories.addAll(getAllChilds( cat ));
        }
        super.insert(transitiveCategories);
    }
    
    private Collection<Category> getAllChilds(Category cat) {
        Set<Category> allChilds = new LinkedHashSet<Category>();
        allChilds.add( cat);
        for ( Category child: cat.getCategories())
        {
            allChilds.addAll(getAllChilds( child ));
        }
        return allChilds;
    }

    private Collection<String> getIds(String parentId) throws SQLException, RaplaException {
		Set<String> childIds = new HashSet<String>();
		String sql = "SELECT ID FROM CATEGORY WHERE PARENT_ID=?";
		PreparedStatement stmt = null;
        ResultSet rset = null;
        try {
            stmt = con.prepareStatement(sql);
            setString(stmt,1, parentId);
            rset = stmt.executeQuery();
            while (rset.next ()) {
            	String id = readId(rset, 1, Category.class);
            	childIds.add( id);
            }
        } finally {
            if (rset != null)
                rset.close();
            if (stmt!=null)
                stmt.close();
        }
        Set<String> result = new HashSet<String>();
        for (String childId : childIds)
        {
        	result.addAll( getIds( childId));
        }
        result.add( parentId);
		return result;
    }

    @Override
	protected int write(PreparedStatement stmt,Category category) throws SQLException, RaplaException {
    	Category root = getSuperCategory();
        if ( category.equals( root ))
            return 0;
        setId( stmt,1, category);
		setId( stmt,2, category.getParent());
        int order = getOrder( category);
        String xml = getXML( category );
        setString(stmt,3, category.getKey());
		setText(stmt,4, xml);
        setInt( stmt,5, order);
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

    @Override
    protected void load(ResultSet rset) throws SQLException, RaplaException {
    	String id = readId(rset, 1, Category.class);
    	String parentId = readId(rset, 2, Category.class, true);

        String xml = getText( rset, 4 );
    	Integer order = getInt(rset, 5 );
        CategoryImpl category;
    	if ( xml != null && xml.length() > 10 )
    	{
    	    category = ((CategoryReader)processXML( Category.TYPE, xml )).getCurrentCategory();
            //cache.remove( category );
    	}
    	else
    	{
			getLogger().warn("Category has empty xml field. Ignoring.");
			return;
    	}
		category.setId( id);
        put( category );

        orderMap.put( category, order);
        // parentId can also be null
        categoriesWithoutParent.put( category, parentId);
    }

    @Override
    public void loadAll() throws RaplaException, SQLException {
    	categoriesWithoutParent.clear();
    	super.loadAll();
    	// then we rebuild the hierarchy
    	Iterator<Map.Entry<Category,String>> it = categoriesWithoutParent.entrySet().iterator();
    	while (it.hasNext()) {
    		Map.Entry<Category,String> entry = it.next();
    		String parentId = entry.getValue();
    		Category category =  entry.getKey();
    		Category parent;
            Assert.notNull( category );
    		if ( parentId != null) {
    		    parent = entityStore.resolve( parentId ,Category.class);
            } else {
    		    parent = getSuperCategory();
            }
            Assert.notNull( parent );
            parent.addCategory( category );
    	}
    }

	@Override
	void insertAll() throws SQLException, RaplaException {
		CategoryImpl superCategory = cache.getSuperCategory();
		Set<Category> childs = new HashSet<Category>();
		addChildren(childs, superCategory);
		insert( childs);
	}
	
	private void addChildren(Collection<Category> list, Category category) {
		for (Category child:category.getCategories())
		{
			list.add( child );
			addChildren(list, child);
		}
	}
}

class AllocatableStorage extends RaplaTypeStorage<Allocatable> {
    Map<String,Classification> classificationMap = new HashMap<String,Classification>();
    Map<String,Allocatable> allocatableMap = new HashMap<String,Allocatable>();
    AttributeValueStorage<Allocatable> resourceAttributeStorage;
    PermissionStorage<Allocatable> permissionStorage;

    public AllocatableStorage(RaplaContext context ) throws RaplaException {
        super(context,Allocatable.TYPE,"RAPLA_RESOURCE",new String [] {"ID VARCHAR(255) NOT NULL PRIMARY KEY","TYPE_KEY VARCHAR(255) NOT NULL","OWNER_ID VARCHAR(255)","CREATION_TIME TIMESTAMP","LAST_CHANGED TIMESTAMP","LAST_CHANGED_BY VARCHAR(255) DEFAULT NULL"});  
        resourceAttributeStorage = new AttributeValueStorage<Allocatable>(context,"RESOURCE_ATTRIBUTE_VALUE", "RESOURCE_ID",classificationMap, allocatableMap);
        permissionStorage = new PermissionStorage<Allocatable>( context, "RESOURCE",allocatableMap);
        addSubStorage(resourceAttributeStorage);
        addSubStorage(permissionStorage );
    }

	@Override
	void insertAll() throws SQLException, RaplaException {
		insert( cache.getAllocatables());
	}
    
    @Override
	protected int write(PreparedStatement stmt,Allocatable entity) throws SQLException,RaplaException {
	  	AllocatableImpl allocatable = (AllocatableImpl) entity;
	  	String typeKey = allocatable.getClassification().getType().getKey();
		setId(stmt, 1, entity);
	  	setString(stmt,2, typeKey );
		org.rapla.entities.Timestamp timestamp = allocatable;
		setId(stmt,3, allocatable.getOwner() );
		setTimestamp(stmt, 4,timestamp.getCreateTime() );
		setTimestamp(stmt, 5,timestamp.getLastChanged() );
		setId( stmt,6,timestamp.getLastChangedBy() );
		stmt.addBatch();
      	return 1;
    }
    @Override
    protected void load(ResultSet rset) throws SQLException, RaplaException {
        String id= readId(rset,1, Allocatable.class);
    	String typeKey = getString(rset,2 , null);
		final Date createDate = getTimestampOrNow( rset, 4);
		final Date lastChanged = getTimestampOrNow( rset, 5);
     	
    	AllocatableImpl allocatable = new AllocatableImpl(createDate, lastChanged);
    	allocatable.setLastChangedBy( resolveFromId(rset, 6, User.class) );
    	allocatable.setId( id);
    	allocatable.setResolver( entityStore);
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
		allocatable.setOwner( resolveFromId(rset, 3, User.class) );
    	Classification classification = ((DynamicTypeImpl)type).newClassificationWithoutCheck(false);
    	allocatable.setClassification( classification );
    	classificationMap.put( id, classification );
    	allocatableMap.put( id, allocatable);
    	put( allocatable );
    }
    
    @Override
	public void loadAll() throws RaplaException, SQLException {
    	classificationMap.clear();
    	super.loadAll();
    }
}

class ReservationStorage extends RaplaTypeStorage<Reservation> {
    Map<String,Classification> classificationMap = new HashMap<String,Classification>();
    Map<String,Reservation> reservationMap = new HashMap<String,Reservation>();
    AttributeValueStorage<Reservation> attributeValueStorage;
    // appointmentstorage is not a sub store but a delegate
	AppointmentStorage appointmentStorage;
	PermissionStorage<Reservation> permissionStorage;

    public ReservationStorage(RaplaContext context) throws RaplaException {
        super(context,Reservation.TYPE, "EVENT",new String [] {"ID VARCHAR(255) NOT NULL PRIMARY KEY","TYPE_KEY VARCHAR(255) NOT NULL","OWNER_ID VARCHAR(255) NOT NULL","CREATION_TIME TIMESTAMP","LAST_CHANGED TIMESTAMP","LAST_CHANGED_BY VARCHAR(255) DEFAULT NULL"});
        attributeValueStorage = new AttributeValueStorage<Reservation>(context,"EVENT_ATTRIBUTE_VALUE","EVENT_ID", classificationMap, reservationMap);
        addSubStorage(attributeValueStorage);
        permissionStorage = new PermissionStorage<Reservation>( context,"EVENT", reservationMap);
        addSubStorage(permissionStorage );
    }
     
    public void setAppointmentStorage(AppointmentStorage appointmentStorage)
    {
    	this.appointmentStorage = appointmentStorage;
    }
    
   	@Override
	void insertAll() throws SQLException, RaplaException {
		insert( cache.getReservations());
	}

	@Override
	public void save(Iterable<Reservation> entities) throws RaplaException,	SQLException {
		super.save(entities);
		Collection<Appointment> appointments = new ArrayList<Appointment>();
		for (Reservation r: entities)
		{
			appointments.addAll( Arrays.asList(r.getAppointments()));
		}
		appointmentStorage.insert( appointments );
	}
	
	@Override
	public void setConnection(Connection con) throws SQLException {
		super.setConnection(con);
		appointmentStorage.setConnection(con);
	}

    @Override
    protected int write(PreparedStatement stmt,Reservation event) throws SQLException,RaplaException {
      	String typeKey = event.getClassification().getType().getKey();
      	setId(stmt,1, event );
      	setString(stmt,2, typeKey );
    	setId(stmt,3, event.getOwner() );
    	org.rapla.entities.Timestamp timestamp = event;
        setTimestamp( stmt,4,timestamp.getCreateTime());
        setTimestamp( stmt,5,timestamp.getLastChanged());
        setId(stmt, 6, timestamp.getLastChangedBy());
        stmt.addBatch();
        return 1;
    }
    
    @Override
	protected void load(ResultSet rset) throws SQLException, RaplaException {
    	final Date createDate = getTimestampOrNow(rset,4);
        final Date lastChanged = getTimestampOrNow(rset,5);
        ReservationImpl event = new ReservationImpl(createDate, lastChanged);
    	String id = readId(rset,1,Reservation.class);
		event.setId( id);
		event.setResolver( entityStore);
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
    	Classification classification = ((DynamicTypeImpl)type).newClassificationWithoutCheck(false);
    	event.setClassification( classification );
    	classificationMap.put( id, classification );
    	reservationMap.put( id, event );
    	put( event );
    }

    @Override
    public void loadAll() throws RaplaException, SQLException {
    	classificationMap.clear();
    	super.loadAll();
    }

    @Override
    public void deleteEntities(Iterable<Reservation> entities)
    		throws SQLException, RaplaException {
    	super.deleteEntities(entities);
    	deleteAppointments(entities);
    }

	private void deleteAppointments(Iterable<Reservation> entities)
			throws SQLException, RaplaException {
		Set<String> ids = new HashSet<String>();
		String sql = "SELECT ID FROM APPOINTMENT WHERE EVENT_ID=?";
		for (Reservation entity:entities)
		{
			PreparedStatement stmt = null;
	        ResultSet rset = null;
	        try {
	            stmt = con.prepareStatement(sql);
	            String eventId = entity.getId();
                setString(stmt,1,  eventId);
	            rset = stmt.executeQuery();
	            while (rset.next ()) {
	            	String appointmentId = readId(rset, 1, Appointment.class);
	            	ids.add( appointmentId);
	            }
	        } finally {
	            if (rset != null)
	                rset.close();
	            if (stmt!=null)
	                stmt.close();
	        }
		}
		// and delete them
		appointmentStorage.deleteIds( ids);
	}
}

class AttributeValueStorage<T extends Entity<T>> extends EntityStorage<T> {
    Map<String,Classification> classificationMap;
    Map<String,? extends Annotatable> annotableMap;
    final String foreignKeyName;
    // TODO Write conversion script to update all old entries to new entries
    public final static String OLD_ANNOTATION_PREFIX = "annotation:";
  	public final static String ANNOTATION_PREFIX = "rapla:";
	
    public AttributeValueStorage(RaplaContext context,String tablename, String foreignKeyName, Map<String,Classification> classificationMap, Map<String, ? extends Annotatable> annotableMap) throws RaplaException {
    	super(context, tablename, new String[]{foreignKeyName + " VARCHAR(255) NOT NULL KEY","ATTRIBUTE_KEY VARCHAR(255)","ATTRIBUTE_VALUE VARCHAR(20000)"});
        this.foreignKeyName = foreignKeyName;
        this.classificationMap = classificationMap;
        this.annotableMap = annotableMap;
    }

    @Override
	protected int write(PreparedStatement stmt,T classifiable) throws EntityNotFoundException, SQLException {
        Classification classification =  ((Classifiable)classifiable).getClassification();
        Attribute[] attributes = classification.getAttributes();
        int count =0;
        for (int i=0;i<attributes.length;i++) {
            Attribute attribute = attributes[i];
            Collection<Object> values = classification.getValues( attribute );
            for (Object value: values)
            {
            	String valueAsString;
            	if ( value instanceof Category || value instanceof Allocatable)
            	{
                    Entity casted = (Entity) value;
                    valueAsString = casted.getId();
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
    	Annotatable annotatable = (Annotatable)classifiable;
    	for ( String key: annotatable.getAnnotationKeys())
    	{
    		String valueAsString = annotatable.getAnnotation( key);
    		setId(stmt,1, classifiable);
	        setString(stmt,2, ANNOTATION_PREFIX + key);
	     	setString(stmt,3, valueAsString);
	     	stmt.addBatch();
            count++;
    	}
        return count;
    }

    @Override
    protected void load(ResultSet rset) throws SQLException, RaplaException {
        Class<? extends Entity> idClass = foreignKeyName.indexOf("RESOURCE")>=0 ? Allocatable.class : Reservation.class;
		String classifiableId = readId(rset, 1, idClass);
        String attributekey = rset.getString( 2 );
        boolean annotationPrefix = attributekey.startsWith(ANNOTATION_PREFIX);
		boolean oldAnnotationPrefix = attributekey.startsWith(OLD_ANNOTATION_PREFIX);
		if ( annotationPrefix || oldAnnotationPrefix)
        {
        	String annotationKey = attributekey.substring( annotationPrefix ? ANNOTATION_PREFIX.length() : OLD_ANNOTATION_PREFIX.length());
        	Annotatable annotatable = annotableMap.get(classifiableId);
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
                getLogger().warn("No resource or reservation found for the id " + classifiableId  + " ignoring.");
        	}
        }
        else
        {
            ClassificationImpl classification = (ClassificationImpl) classificationMap.get(classifiableId);
            if ( classification == null) {
                getLogger().warn("No resource or reservation found for the id " + classifiableId  + " ignoring.");
                return;
            }
	    	Attribute attribute = classification.getType().getAttribute( attributekey );
	    	if ( attribute == null) {
	    		getLogger().error("DynamicType '" +classification.getType() +"' doesnt have an attribute with the key " + attributekey + " Current allocatable/reservation Id " + classifiableId + ". Ignoring attribute.");
	    		return;
	    	}
	    	String valueAsString = rset.getString( 3);
    	    if ( valueAsString != null )
            {
    	        try
    	        {
    	            Object value = AttributeImpl.parseAttributeValue(attribute, valueAsString);
    	            if ( value != null)
    	            {
    	                classification.addValue( attribute, value);
    	            }
    	        }
    	        catch(RaplaException e)
    	        {
                    getLogger().error("DynamicType '" +classification.getType() +"' doesnt have a valid attribute with the key " + attributekey + " Current allocatable/reservation Id " + classifiableId + ". Ignoring attribute.");
    	        }
            }
        }
    }
}

 class PermissionStorage<T extends EntityPermissionContainer<T>> extends EntityStorage<T>  {
    Map<String,T> referenceMap;
    public PermissionStorage(RaplaContext context,String type,Map<String,T> idMap) throws RaplaException {
        super(context,type+"_PERMISSION",new String[] {type + "_ID VARCHAR(255) NOT NULL KEY","USER_ID VARCHAR(255)","GROUP_ID VARCHAR(255)","ACCESS_LEVEL INTEGER NOT NULL","MIN_ADVANCE INTEGER","MAX_ADVANCE INTEGER","START_DATE DATETIME","END_DATE DATETIME"});
        this.referenceMap = idMap;
    }

    protected int write(PreparedStatement stmt, EntityPermissionContainer container) throws SQLException, RaplaException {
        int count = 0;
        Iterable<Permission> permissionList = container.getPermissionList();
        for (Permission s:permissionList) {
			setId(stmt,1,container);
			setId(stmt,2,s.getUser());
			setId(stmt,3,s.getGroup());
			@SuppressWarnings("deprecation")
            int numericLevel = s.getAccessLevel().getNumericLevel();
            setInt(stmt,4, numericLevel);
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
        Class<? extends Entity> clazz = null;
        String referenceIdInt = readId(rset, 1, clazz);
        PermissionContainer allocatable = referenceMap.get(referenceIdInt);
        if ( allocatable == null)
        {
        	getLogger().warn("Could not find resource object with id "+ referenceIdInt + " for permission. Maybe the resource was deleted from the database.");
        	return;
        }
        PermissionImpl permission = new PermissionImpl();
        permission.setUser( resolveFromId(rset, 2, User.class));
        permission.setGroup( resolveFromId(rset, 3, Category.class));
        Integer accessLevel = getInt( rset, 4);
        if  ( accessLevel !=null)
        {
	        AccessLevel enumLevel = AccessLevel.find(accessLevel);
	        permission.setAccessLevel( enumLevel );
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
        super(context, Appointment.TYPE,"APPOINTMENT",new String [] {"ID VARCHAR(255) NOT NULL PRIMARY KEY","EVENT_ID VARCHAR(255) NOT NULL KEY","APPOINTMENT_START DATETIME NOT NULL","APPOINTMENT_END DATETIME NOT NULL","REPETITION_TYPE VARCHAR(255)","REPETITION_NUMBER INTEGER","REPETITION_END DATETIME","REPETITION_INTERVAL INTEGER"});
        appointmentExceptionStorage = new AppointmentExceptionStorage(context);
        allocationStorage = new AllocationStorage( context);
        addSubStorage(appointmentExceptionStorage);
        addSubStorage(allocationStorage);
    }

	@Override
	void insertAll() throws SQLException, RaplaException {
		Collection<Reservation> reservations = cache.getReservations();
		Collection<Appointment> appointments = new LinkedHashSet<Appointment>();
		for (Reservation r: reservations)
		{
			appointments.addAll( Arrays.asList(r.getAppointments()));
		}
		insert( appointments);
	}

    @Override
    protected int write(PreparedStatement stmt,Appointment appointment) throws SQLException,RaplaException {
      	setId( stmt, 1, appointment);
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

    @Override
	protected void load(ResultSet rset) throws SQLException, RaplaException {
        String id = readId(rset, 1, Appointment.class);
        Reservation event = resolveFromId(rset, 2, Reservation.class);
        if ( event == null)
        {
        	return;
        }
        Date start = getDate(rset,3);
        Date end = getDate(rset,4);
        boolean wholeDayAppointment = start.getTime() == DateTools.cutDate( start.getTime()) && end.getTime() == DateTools.cutDate( end.getTime());
    	AppointmentImpl appointment = new AppointmentImpl(start, end);
    	appointment.setId( id);
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
        super(context,"ALLOCATION",new String [] {"APPOINTMENT_ID VARCHAR(255) NOT NULL KEY", "RESOURCE_ID VARCHAR(255) NOT NULL", "PARENT_ORDER INTEGER"});
    }
    
    @Override
    protected int write(PreparedStatement stmt, Appointment appointment) throws SQLException, RaplaException {
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
  
    @Override
    protected void load(ResultSet rset) throws SQLException, RaplaException {
    	Appointment appointment =resolveFromId(rset,1, Appointment.class);
    	if ( appointment == null)
    	{
    		return;
    	}
        ReservationImpl event = (ReservationImpl) appointment.getReservation();
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
        if (event.getAppointmentList().size() > newAppointments.length ) {
            event.setRestriction( allocatable, newAppointments );
        } else {
            event.setRestriction( allocatable, new Appointment[] {} );
        }
    }

 }

class AppointmentExceptionStorage extends EntityStorage<Appointment>  {
    public AppointmentExceptionStorage(RaplaContext context) throws RaplaException {
        super(context,"APPOINTMENT_EXCEPTION",new String [] {"APPOINTMENT_ID VARCHAR(255) NOT NULL KEY","EXCEPTION_DATE DATETIME NOT NULL"});
    }

    @Override
    protected int write(PreparedStatement stmt, Appointment entity) throws SQLException, RaplaException {
        Repeating repeating = entity.getRepeating();
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

    @Override
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
        super(context, DynamicType.TYPE,"DYNAMIC_TYPE", new String [] {"ID VARCHAR(255) NOT NULL PRIMARY KEY","TYPE_KEY VARCHAR(255) NOT NULL","DEFINITION TEXT NOT NULL"});//, "CREATION_TIME TIMESTAMP","LAST_CHANGED TIMESTAMP","LAST_CHANGED_BY INTEGER DEFAULT NULL"});
    }

    @Override
	protected int write(PreparedStatement stmt, DynamicType type) throws SQLException, RaplaException {
		if (((DynamicTypeImpl) type).isInternal())
		{
			return 0;
		}
        setId(stmt,1,type);
        setString(stmt,2, type.getKey());
        setText(stmt,3,  getXML( type) );
//    	setDate(stmt, 4,timestamp.getCreateTime() );
//    	setDate(stmt, 5,timestamp.getLastChanged() );
//    	setId( stmt,6,timestamp.getLastChangedBy() );
        stmt.addBatch();
        return 1;
    }
	
	@Override
	void insertAll() throws SQLException, RaplaException {
		Collection<DynamicType> dynamicTypes = new ArrayList<DynamicType>(cache.getDynamicTypes());
		Iterator<DynamicType> it = dynamicTypes.iterator();
		while ( it.hasNext())
		{
		    if (((DynamicTypeImpl)it.next()).isInternal())
		    {
		        it.remove();
		    }
		}
        insert( dynamicTypes);
	}

	protected void load(ResultSet rset) throws SQLException,RaplaException {
    	@SuppressWarnings("unused")
        String id = readId(rset, 1, DynamicType.class);
	    String xml = getText(rset,3);
    	@SuppressWarnings("unused")
        RaplaXMLReader reader = processXML( DynamicType.TYPE, xml );
//    	final Date createDate = getDate( rset, 4);
//    	final Date lastChanged = getDate( rset, 5);
//     	
//    	AllocatableImpl allocatable = new AllocatableImpl(createDate, lastChanged);
//    	allocatable.setLastChangedBy( resolveFromId(rset, 6, User.class) );
    	
  //  	DynamicType type = reader.getStore().resolve(id, DynamicType.class);
//    	idMap.put( id, type);
	}

}


class PreferenceStorage extends RaplaTypeStorage<Preferences> 
{
    public PreferenceStorage(RaplaContext context) throws RaplaException {
        super(context,Preferences.TYPE,"PREFERENCE",
	    new String [] {"USER_ID VARCHAR(255) KEY","ROLE VARCHAR(255) NOT NULL","STRING_VALUE VARCHAR(10000)","XML_VALUE TEXT"});
    }

    class PatchEntry
    {
        String userId;
        String role;
    }
    
	public void storePatches(List<PreferencePatch> preferencePatches) throws RaplaException, SQLException 
	{
	    for ( PreferencePatch patch:preferencePatches)
	    {
	        String userId = patch.getUserId();
            PreparedStatement stmt = null;
	        try {
	            
	            final String deleteSqlWithRole;
                int count = 0;
	            if ( userId != null)
	            {
	                deleteSqlWithRole = deleteSql + " and role=?";
	                stmt = con.prepareStatement(deleteSqlWithRole);
	                for ( String role: patch.getRemovedEntries())
	                {
	                    setId(stmt, 1, userId);
	                    setString(stmt,2,role);
	                    stmt.addBatch();
	                    count++;
	                }
	                for ( String role: patch.keySet())
	                {
	                    setId(stmt, 1, userId);
	                    setString(stmt,2,role);
	                    stmt.addBatch();
	                    count++;
	                }
	            }
	            else
	            {
	                deleteSqlWithRole = "delete from " + tableName + " where user_id IS null and role=?";
                    stmt = con.prepareStatement(deleteSqlWithRole);
                    for ( String role: patch.getRemovedEntries())
                    {
                        //setId(stmt, 1, userId);
                        setString(stmt,1,role);
                        stmt.addBatch();
                        count++;
                    }
                    for ( String role: patch.keySet())
                    {
                        //setId(stmt, 1, userId);
                        setString(stmt,1,role);
                        stmt.addBatch();
                        count++;
                    }
	            }
	            
	            if ( count > 0)
	            {
	                stmt.executeBatch();
	            } 
	        } finally {
	            if (stmt!=null)
	                stmt.close();
	        }
	    }
	    
	    PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement(insertSql);
            int count = 0;
            for ( PreferencePatch patch:preferencePatches)
            {
                String userId = patch.getUserId();
                for ( String role:patch.keySet())
                {
                    Object entry = patch.get( role);
                    insterEntry(stmt, userId, role, entry);
                    count++;
                }
            }

            if ( count > 0)
            {
                stmt.executeBatch();
            } 
        } catch (SQLException ex) {
            throw ex;
        } finally {
            if (stmt!=null)
                stmt.close();
        }
    }

    @Override
	void insertAll() throws SQLException, RaplaException {
		List<Preferences> preferences = new ArrayList<Preferences>();
		{
			PreferencesImpl systemPrefs = cache.getPreferencesForUserId(null);
			if ( systemPrefs != null)
			{
				preferences.add( systemPrefs);
			}
		}
		Collection<User> users = cache.getUsers();
		for ( User user:users)
		{
			String userId = user.getId();
			PreferencesImpl userPrefs = cache.getPreferencesForUserId(userId);
			if ( userPrefs != null)
			{
				preferences.add( userPrefs);
			}
		}
		insert( preferences);
	}

    @Override
    protected int write(PreparedStatement stmt, Preferences entity) throws SQLException, RaplaException {
        PreferencesImpl preferences = (PreferencesImpl) entity;
        User user = preferences.getOwner();
        String userId = user != null ? user.getId():null; 
        int count = 0;
        for (String role:preferences.getPreferenceEntries()) {
            Object entry = preferences.getEntry(role);
            insterEntry(stmt, userId, role, entry);
            count++;
        }
       
        return count;
    }

    private void insterEntry(PreparedStatement stmt, String userId, String role, Object entry) throws SQLException, RaplaException {
        setString( stmt, 1, userId);
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
    }

    @Override
    protected void load(ResultSet rset) throws SQLException, RaplaException {
    	//findPreferences
    	//check if value set
    	//  yes read value
    	//  no read xml

        String userId = readId(rset, 1,User.class, true);
        User owner;
        if  ( userId == null || userId.equals(Preferences.SYSTEM_PREFERENCES_ID) )
        {
        	owner = null;
        }
        else
        {
        	User user = entityStore.tryResolve( userId,User.class);
        	if ( user != null)
        	{
        		owner = user;
        	}
        	else
        	{
        		getLogger().warn("User with id  " + userId + " not found ingnoring preference entry.");
        		return;
        	}
        }
   
        String configRole = getString( rset, 2, null);
        String preferenceId = PreferencesImpl.getPreferenceIdFromUser(userId);
        if ( configRole == null)
        {
        	getLogger().warn("Configuration role for " + preferenceId + " is null. Ignoring preference entry.");
        	return;
        }
        String value = getString( rset,3, null);
//        if (PreferencesImpl.isServerEntry(configRole))
//        {
//        	entityStore.putServerPreferences(owner,configRole, value);
//        	return;
//        }
        
        PreferencesImpl preferences = preferenceId != null ? (PreferencesImpl) entityStore.tryResolve( preferenceId, Preferences.class ): null;
        if ( preferences == null) 
        {
            Date now =getCurrentTimestamp();
            preferences = new PreferencesImpl(now,now);
            preferences.setId(preferenceId);
            preferences.setOwner(owner);
            put( preferences );
        }
      
        if ( value!= null) {
            preferences.putEntryPrivate(configRole, value);
        } else {
        	String xml = getText(rset, 4);
	        if ( xml != null && xml.length() > 0)
	        {
		        PreferenceReader contentHandler = (PreferenceReader) processXML( Preferences.TYPE, xml );
		        RaplaObject type = contentHandler.getChildType();
		        preferences.putEntryPrivate(configRole, type);
	        }
        }
    }

    @Override
    public void deleteEntities( Iterable<Preferences> entities) throws SQLException {
        PreparedStatement stmt = null;
        boolean deleteNullUserPreference = false;
        try {
            stmt = con.prepareStatement(deleteSql);
            boolean empty = true;
            for ( Preferences preferences: entities)
            {
                User user = preferences.getOwner();
                if ( user == null) {
                	deleteNullUserPreference = true;
                }
                empty = false;
            	setId( stmt,1, user);
                stmt.addBatch();
            }
            if ( !empty)
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
	    new String [] {"ID VARCHAR(255) NOT NULL PRIMARY KEY","USERNAME VARCHAR(255) NOT NULL","PASSWORD VARCHAR(255)","NAME VARCHAR(255) NOT NULL","EMAIL VARCHAR(255) NOT NULL","ISADMIN INTEGER NOT NULL", "CREATION_TIME TIMESTAMP", "LAST_CHANGED TIMESTAMP"});
        groupStorage = new UserGroupStorage( context );
        addSubStorage( groupStorage );
    }

    @Override
	void insertAll() throws SQLException, RaplaException {
		insert( cache.getUsers());
	}
	
    @Override
    protected int write(PreparedStatement stmt,User user) throws SQLException, RaplaException {
    	setId(stmt, 1, user);
    	setString(stmt,2,user.getUsername());
    	String password = cache.getPassword(user.getId());
    	setString(stmt,3,password);
    	//setId(stmt,4,user.getPerson());
    	setString(stmt,4,user.getName());
    	setString(stmt,5,user.getEmail());
    	stmt.setInt(6,user.isAdmin()?1:0);
    	setTimestamp(stmt, 7, user.getCreateTime() );
   		setTimestamp(stmt, 8, user.getLastChanged() );
   		stmt.addBatch();
   		return 1;
    }
    
    @Override
    protected void load(ResultSet rset) throws SQLException, RaplaException {
        String userId = readId(rset,1, User.class );
        String username = getString(rset,2, null);
        if ( username == null)
        {
        	getLogger().warn("Username is null for " + userId + " Ignoring user.");
        }
        String password = getString(rset,3, null);
        //String personId = readId(rset,4, Allocatable.class, true);
        String name = getString(rset,4,"");
        String email = getString(rset,5,"");
        boolean isAdmin = rset.getInt(6) == 1;
        Date createDate = getTimestampOrNow( rset, 7);
		Date lastChanged = getTimestampOrNow( rset, 8);
     	
        UserImpl user = new UserImpl(createDate, lastChanged);
//        if ( personId != null)
//        {
//            user.putId("person", personId);
//        }
        user.setId( userId );
        user.setUsername( username );
        user.setName( name );
        user.setEmail( email );
        user.setAdmin( isAdmin );
        if ( password != null) {
            putPassword(userId,password);
        }
        put(user);
   }

}

class ConflictStorage extends RaplaTypeStorage<Conflict> {
    
    public ConflictStorage(RaplaContext context) throws RaplaException {
        super( context,Conflict.TYPE, "RAPLA_CONFLICT",
        new String [] {"RESOURCE_ID VARCHAR(255) NOT NULL","APPOINTMENT1 VARCHAR(255) NOT NULL","APPOINTMENT2 VARCHAR(255) NOT NULL","APP1ENABLED INTEGER NOT NULL","APP2ENABLED INTEGER NOT NULL", "LAST_CHANGED TIMESTAMP"});
        this.deleteSql = "delete from " + tableName + " where RESOURCE_ID=? and APPOINTMENT1=? and APPOINTMENT2=?";
    }

    @Override
    void insertAll() throws SQLException, RaplaException {
        insert( cache.getDisabledConflicts());
    }
    
    
    public void deleteIds(Collection<String> ids) throws SQLException, RaplaException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement(deleteSql);
            for ( String id: ids)
            {
                Conflict conflict = new ConflictImpl(id, null);
                String allocatableId = conflict.getAllocatableId();
                String appointment1Id = conflict.getAppointment1();
                String appointment2Id = conflict.getAppointment2();
                stmt.setString(1,allocatableId);
                stmt.setString(2,appointment1Id);
                stmt.setString(3,appointment2Id);
                stmt.addBatch();
            }
            if ( ids.size() > 0)
            {
                stmt.executeBatch();
            }
        } finally {
            if (stmt!=null)
                stmt.close();
        }
    }

    
    @Override
    protected int write(PreparedStatement stmt,Conflict conflict) throws SQLException, RaplaException {
        String allocatableId = conflict.getAllocatableId();
        setId(stmt, 1, allocatableId);
        setId(stmt, 2, conflict.getAppointment1());
        setId(stmt, 3, conflict.getAppointment2());
        boolean appointment1Enabled = conflict.isAppointment1Enabled();
        setInt(stmt, 4, appointment1Enabled ? 1:0);
        boolean appointment2Enabled = conflict.isAppointment2Enabled();
        setInt(stmt, 5, appointment2Enabled ? 1:0);
        setTimestamp(stmt, 6, getCurrentTimestamp() );
        stmt.addBatch();
        return 1;
    }
    
    
    @Override
    protected void load(ResultSet rset) throws SQLException, RaplaException {
        String allocatableId = readId(rset,1, Allocatable.class );
        String appointment1Id = readId(rset,2, Appointment.class );
        String appointment2Id = readId(rset,3, Appointment.class );

        boolean appointment1Enabled = rset.getInt(4) == 1;
        boolean appointment2Enabled = rset.getInt(5) == 1;
        
        Date today = getCurrentTimestamp();
        String id = ConflictImpl.createId(allocatableId, appointment1Id, appointment2Id); 
        ConflictImpl conflict = new ConflictImpl(id, today);
        conflict.setAppointment1Enabled(appointment1Enabled );
        conflict.setAppointment2Enabled(appointment2Enabled );
        put(conflict);
   }

}

class UserGroupStorage extends EntityStorage<User> {
    public UserGroupStorage(RaplaContext context) throws RaplaException {
        super(context,"RAPLA_USER_GROUP", new String [] {"USER_ID VARCHAR(255) NOT NULL KEY","CATEGORY_ID VARCHAR(255) NOT NULL"});
    }

    @Override
    protected int write(PreparedStatement stmt, User entity) throws SQLException, RaplaException {
        setId( stmt,1, entity);
        int count = 0;
        for (Category category:entity.getGroupList()) {
            setId(stmt, 2, category);
            stmt.addBatch();
            count++;
        }
        return count;
    }

    @Override
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




