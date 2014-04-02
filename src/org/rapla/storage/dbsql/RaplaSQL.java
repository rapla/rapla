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
import java.sql.Statement;
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
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.ResourceAnnotations;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.entities.domain.internal.PeriodImpl;
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
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.storage.xml.CategoryReader;
import org.rapla.storage.xml.PreferenceReader;
import org.rapla.storage.xml.RaplaXMLReader;
import org.rapla.storage.xml.RaplaXMLWriter;

class RaplaSQL {
    private final List<RaplaTypeStorage> stores = new ArrayList<RaplaTypeStorage>();
    private final Logger logger;
    RaplaContext context;
    
    
    RaplaSQL( RaplaContext context) throws RaplaException{
    	this.context = context;
        logger =  context.lookup( Logger.class);
        // The order is important. e.g. appointments can only be loaded if the reservation they are refering to are already loaded.
	    stores.add(new CategoryStorage( context));
	    stores.add(new DynamicTypeStorage( context));
	    stores.add(new UserStorage( context));
	    stores.add(new AllocatableStorage( context));
	    stores.add(new PreferenceStorage( context));
	    ReservationStorage reservationStorage = new ReservationStorage( context);
		stores.add(reservationStorage);
	    AppointmentStorage appointmentStorage = new AppointmentStorage( context);
		stores.add(appointmentStorage);
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
		TableDef tableDef = schema.get("PERIOD");
		if ( tableDef != null)
		{
			PeriodStorage storage = new PeriodStorage(context);
			storage.setConnection(con);
			try
			{
				storage.loadAll();
				Collection<PeriodImpl> periods = storage.getPeriods();
				// FIXME implement period conversion
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

class PeriodStorage extends EntityStorage {
	List<PeriodImpl> result = new ArrayList<PeriodImpl>();
    public PeriodStorage(RaplaContext context) throws RaplaException {
    	super(context,"PERIOD",new String[] {"ID INTEGER NOT NULL PRIMARY KEY","NAME VARCHAR(255) NOT NULL","PERIOD_START DATETIME NOT NULL","PERIOD_END DATETIME NOT NULL"});
    }

    @Override
    public void createOrUpdateIfNecessary(Map schema) throws SQLException,	RaplaException {
    }
    
    @Override
    protected int write(PreparedStatement stmt,Entity period) throws SQLException {
    	throw new IllegalStateException("Should not be called as periods are now stored as allocatables");
    }

    @Override
    protected void load(ResultSet rset) throws SQLException {
		String id = Period.TYPE.getId( rset.getInt(1));
		String name = getString(rset,2, null);
		if ( name == null)
		{
			getLogger().warn("Name is null for " + id + ". Ignored");
		}
		java.util.Date von  = getDate(rset,3);
		java.util.Date bis  = getDate(rset,4);
		PeriodImpl period = new PeriodImpl(name,von,bis);
		result.add( period);
    }
    
    Collection<PeriodImpl> getPeriods()
    {
    	return result;
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
    	super(context,Category.TYPE, "CATEGORY",new String[] {"ID INTEGER NOT NULL PRIMARY KEY","PARENT_ID INTEGER KEY","CATEGORY_KEY VARCHAR(100) NOT NULL","DEFINITION TEXT NOT NULL","PARENT_ORDER INTEGER"});
    }
    
    @Override
    public void createOrUpdateIfNecessary(Map<String, TableDef> schema) throws SQLException, RaplaException
	{
    	super.createOrUpdateIfNecessary( schema);
    	checkAndDrop(schema, "NAME");
    	checkAndAdd(schema, "DEFINITION");
    	checkAndAdd(schema, "PARENT_ORDER");
    	checkAndRetype(schema, "DEFINITION");
	}
    
    @Override
    public void deleteEntities(Collection<Category> entities) throws SQLException,RaplaException {
    	Set<String> idList = new HashSet<String>();
    	for ( Category cat:entities)
    	{
    		idList.addAll( getIds( cat.getId()));
    	}
    	deleteIds(idList);
    }
    
    @Override
    public void insert(Collection<Category> entities) throws SQLException, RaplaException {
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
            int intId = RaplaType.parseId(parentId);
            stmt.setInt(1, intId);
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
    		    parent = (Category) resolve( parentId );
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
    PermissionStorage permissionStorage;

    public AllocatableStorage(RaplaContext context ) throws RaplaException {
        super(context,Allocatable.TYPE,"RAPLA_RESOURCE",new String [] {"ID INTEGER NOT NULL PRIMARY KEY","TYPE_KEY VARCHAR(100) NOT NULL","OWNER_ID INTEGER","CREATION_TIME TIMESTAMP","LAST_CHANGED TIMESTAMP","LAST_CHANGED_BY INTEGER DEFAULT NULL"});  
        resourceAttributeStorage = new AttributeValueStorage<Allocatable>(context,"RESOURCE_ATTRIBUTE_VALUE", "RESOURCE_ID",classificationMap, allocatableMap);
        permissionStorage = new PermissionStorage( context, allocatableMap);
        addSubStorage(resourceAttributeStorage);
        addSubStorage(permissionStorage );
    }

	@Override
	void insertAll() throws SQLException, RaplaException {
		insert( cache.getAllocatables());
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
	    checkAndConvertIgnoreConflictsField(schema);
    }
    
    protected void checkAndConvertIgnoreConflictsField(Map<String, TableDef> schema) throws SQLException, RaplaException {
    	TableDef tableDef = schema.get(tableName);

		String columnName = "IGNORE_CONFLICTS";
		if (tableDef.getColumn(columnName) != null) 
        {
			getLogger().warn("Patching Database for table " + tableName + " converting " + columnName + " column to conflictCreation annotation in RESOURCE_ATTRIBUTE_VALUE");
        	Map<String, Boolean> map = new HashMap<String,Boolean>();
            {
            	String sql = "SELECT ID," + columnName +" from " + tableName ;
            	Statement stmt = null;
            	ResultSet rset = null;
		        try {
		            stmt = con.createStatement();
		            rset = stmt.executeQuery(sql);
		            while (rset.next ()) {
		            	String id= readId(rset,1, Allocatable.class);
		             	Boolean ignoreConflicts = getInt( rset, 2 ) == 1;
		             	map.put( id, ignoreConflicts);
		            }
		        } finally {
		            if (rset != null)
		                rset.close();
		            if (stmt!=null)
		                stmt.close();
		        }
            }
            {
            	PreparedStatement stmt = null;
                try {
                	String valueString = " (RESOURCE_ID,ATTRIBUTE_KEY,ATTRIBUTE_VALUE)";
					String table = "RESOURCE_ATTRIBUTE_VALUE";
					String insertSql = "insert into " + table + valueString + " values (" + getMarkerList(3) + ")";
                    stmt = con.prepareStatement(insertSql);
                    int count = 0;
                    for ( String id:map.keySet())
                    {
                    	Boolean entry = map.get( id);
                    	if ( entry != null && entry == true)
                    	{
                    	    int idInt = RaplaType.parseId(id);
                		    stmt.setInt( 1, idInt );
                    		setString(stmt,2, AttributeValueStorage.ANNOTATION_PREFIX + ResourceAnnotations.KEY_CONFLICT_CREATION);
                	     	setString(stmt,3, ResourceAnnotations.VALUE_CONFLICT_CREATION_IGNORE);
                	     	stmt.addBatch();
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
            con.commit();
        }
		checkAndDrop(schema,columnName);
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
    	Classification classification = type.newClassification(false);
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

    public ReservationStorage(RaplaContext context) throws RaplaException {
        super(context,Reservation.TYPE, "EVENT",new String [] {"ID INTEGER NOT NULL PRIMARY KEY","TYPE_KEY VARCHAR(100) NOT NULL","OWNER_ID INTEGER NOT NULL","CREATION_TIME TIMESTAMP","LAST_CHANGED TIMESTAMP","LAST_CHANGED_BY INTEGER DEFAULT NULL"});
        attributeValueStorage = new AttributeValueStorage<Reservation>(context,"EVENT_ATTRIBUTE_VALUE","EVENT_ID", classificationMap, reservationMap);
        addSubStorage(attributeValueStorage);
    }
     
    public void setAppointmentStorage(AppointmentStorage appointmentStorage)
    {
    	this.appointmentStorage = appointmentStorage;
    }
    
    @Override
    public void createOrUpdateIfNecessary( Map<String, TableDef> schema) throws SQLException, RaplaException
    {
    	super.createOrUpdateIfNecessary( schema);
    	checkAndAdd(schema, "LAST_CHANGED_BY");
    }

	@Override
	void insertAll() throws SQLException, RaplaException {
		insert( cache.getReservations());
	}

	@Override
	public void save(Collection<Reservation> entities) throws RaplaException,
			SQLException {
		super.save(entities);
		Collection<Appointment> appointments = new ArrayList<Appointment>();
		for (Reservation r: entities)
		{
			appointments.addAll( Arrays.asList(r.getAppointments()));
		}
		appointmentStorage.insert( appointments );
	}
	

	@Override
	public void insert(Collection<Reservation> entities) throws SQLException,RaplaException {
		super.insert(entities);
	
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
    	Classification classification = type.newClassification(false);
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
    public void deleteEntities(Collection<Reservation> entities)
    		throws SQLException, RaplaException {
    	super.deleteEntities(entities);
    	deleteAppointments(entities);
    }

	private void deleteAppointments(Collection<Reservation> entities)
			throws SQLException, RaplaException {
		Set<String> ids = new HashSet<String>();
		String sql = "SELECT ID FROM APPOINTMENT WHERE EVENT_ID=?";
		for (Reservation entity:entities)
		{
			PreparedStatement stmt = null;
	        ResultSet rset = null;
	        try {
	            stmt = con.prepareStatement(sql);
	            stmt.setInt(1, getId( entity));
	            rset = stmt.executeQuery();
	            while (rset.next ()) {
	            	String id = readId(rset, 1, Appointment.class);
	            	ids.add( id);
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
                    Category casted = (Category) value;
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
    	    	Object value = AttributeImpl.parseAttributeValue(attribute, valueAsString);
    	    	classification.addValue( attribute, value);
            }
        }
    }
}

class PermissionStorage extends EntityStorage<Allocatable>  {
    Map<String,Allocatable> allocatableMap;
    public PermissionStorage(RaplaContext context,Map<String,Allocatable> allocatableMap) throws RaplaException {
        super(context,"PERMISSION",new String[] {"RESOURCE_ID INTEGER NOT NULL KEY","USER_ID INTEGER","GROUP_ID INTEGER","ACCESS_LEVEL INTEGER NOT NULL","MIN_ADVANCE INTEGER","MAX_ADVANCE INTEGER","START_DATE DATETIME","END_DATE DATETIME"});
        this.allocatableMap = allocatableMap;
    }

    protected int write(PreparedStatement stmt, Allocatable allocatable) throws SQLException, RaplaException {
        int count = 0;
        for (Permission s:allocatable.getPermissions()) {
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
        String allocatableIdInt = readId(rset, 1, Allocatable.class);
        Allocatable allocatable = allocatableMap.get(allocatableIdInt);
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
        super(context,"ALLOCATION",new String [] {"APPOINTMENT_ID INTEGER NOT NULL KEY", "RESOURCE_ID INTEGER NOT NULL", "OPTIONAL INTEGER"});
    }
    
    @Override
    public void createOrUpdateIfNecessary(
    		Map<String, TableDef> schema) throws SQLException, RaplaException {
    	super.createOrUpdateIfNecessary( schema);
    	checkAndAdd( schema, "OPTIONAL");
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
        super(context,"APPOINTMENT_EXCEPTION",new String [] {"APPOINTMENT_ID INTEGER NOT NULL KEY","EXCEPTION_DATE DATETIME NOT NULL"});
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
        super(context, DynamicType.TYPE,"DYNAMIC_TYPE", new String [] {"ID INTEGER NOT NULL PRIMARY KEY","TYPE_KEY VARCHAR(100) NOT NULL","DEFINITION TEXT NOT NULL"});//, "CREATION_TIME TIMESTAMP","LAST_CHANGED TIMESTAMP","LAST_CHANGED_BY INTEGER DEFAULT NULL"});
    }

    @Override
    public void createOrUpdateIfNecessary(Map<String, TableDef> schema)
    		throws SQLException, RaplaException {
    	super.createOrUpdateIfNecessary(schema);
    	checkAndRetype(schema, "DEFINITION");
//	    checkAndAdd( schema, "CREATION_TIME");
//	    checkAndAdd( schema, "LAST_CHANGED");
//	    checkAndAdd( schema, "LAST_CHANGED_BY");
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
		insert( cache.getDynamicTypes());
	}

	protected void load(ResultSet rset) throws SQLException,RaplaException {
    	String xml = getText(rset,3);
    	processXML( DynamicType.TYPE, xml );
//    	final Date createDate = getDate( rset, 4);
//    	final Date lastChanged = getDate( rset, 5);
//     	
//    	AllocatableImpl allocatable = new AllocatableImpl(createDate, lastChanged);
//    	allocatable.setLastChangedBy( resolveFromId(rset, 6, User.class) );

	}

}


class PreferenceStorage extends RaplaTypeStorage<Preferences> 
{
    public PreferenceStorage(RaplaContext context) throws RaplaException {
        super(context,Preferences.TYPE,"PREFERENCE",
	    new String [] {"USER_ID INTEGER KEY","ROLE VARCHAR(255) NOT NULL","STRING_VALUE VARCHAR(10000)","XML_VALUE TEXT"});
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
    public void createOrUpdateIfNecessary(Map<String, TableDef> schema)
    		throws SQLException, RaplaException {
    	super.createOrUpdateIfNecessary(schema);
    	checkAndRetype(schema, "STRING_VALUE");
    	checkAndRetype(schema, "XML_VALUE");
    }

    @Override
    protected int write(PreparedStatement stmt, Preferences entity) throws SQLException, RaplaException {
        PreferencesImpl preferences = (PreferencesImpl) entity;
        User user = preferences.getOwner();
        if ( user == null) {
			stmt.setInt(1, 0);
        } else {
            stmt.setInt(1,getId( (Entity) user));
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

    @Override
    protected void load(ResultSet rset) throws SQLException, RaplaException {
    	//findPreferences
    	//check if value set
    	//  yes read value
    	//  no read xml

        String userId = readId(rset, 1,User.class, true);
        User owner ;
        if  ( userId.equals(User.TYPE.getId(0)) )
        {
        	owner = null;
        }
        else
        {
        	User user = (User) entityStore.tryResolve( userId);
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
        
        PreferencesImpl preferences = (PreferencesImpl) get( preferenceId );
        if ( preferences == null) 
        {
        	Date now =getCurrentTimestamp();
			preferences = new PreferencesImpl(now,now);
        	preferences.setId(preferenceId);
        	preferences.setOwner(owner);
        	put( preferences );
        }
      
        if ( value!= null) {
            preferences.putEntry(configRole, value);
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
    public void deleteEntities( Collection<Preferences> entities) throws SQLException {
        PreparedStatement stmt = null;
        boolean deleteNullUserPreference = false;
        try {
            stmt = con.prepareStatement(deleteSql);
            for ( Preferences preferences: entities)
            {
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
	    new String [] {"ID INTEGER NOT NULL PRIMARY KEY","USERNAME VARCHAR(100) NOT NULL","PASSWORD VARCHAR(100)","NAME VARCHAR(255) NOT NULL","EMAIL VARCHAR(255) NOT NULL","ISADMIN INTEGER NOT NULL", "CREATION_TIME TIMESTAMP", "LAST_CHANGED TIMESTAMP"});
        groupStorage = new UserGroupStorage( context );
        addSubStorage( groupStorage );
    }

    @Override
    public void createOrUpdateIfNecessary(Map<String, TableDef> schema) throws SQLException, RaplaException {
    	super.createOrUpdateIfNecessary(schema);
	    checkAndAdd( schema, "CREATION_TIME");
	    checkAndAdd( schema, "LAST_CHANGED");
    }
    
	@Override
	void insertAll() throws SQLException, RaplaException {
		insert( cache.getUsers());
	}
	
    @Override
    protected int write(PreparedStatement stmt,User user) throws SQLException, RaplaException {
    	setId(stmt, 1, user);
    	stmt.setString(2,user.getUsername());
    	String password = cache.getPassword(user.getId());
    	stmt.setString(3,password);
    	stmt.setString(4,user.getName());
    	stmt.setString(5,user.getEmail());
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
        String name = getString(rset,4,"");
        String email = getString(rset,5,"");
        boolean isAdmin = rset.getInt(6) == 1;
        Date createDate = getTimestampOrNow( rset, 7);
		Date lastChanged = getTimestampOrNow( rset, 8);
     	
        UserImpl user = new UserImpl(createDate, lastChanged);
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

    @Override
    protected int write(PreparedStatement stmt, User entity) throws SQLException, RaplaException {
        setId( stmt,1, entity);
        int count = 0;
        for (Category category:entity.getGroups()) {
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




