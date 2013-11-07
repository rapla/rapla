package org.rapla.storage;

import java.util.HashMap;
import java.util.Map;

import org.rapla.entities.Category;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.Conflict;
import org.rapla.framework.Provider;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaDefaultContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.storage.impl.EntityStore;
import org.rapla.storage.xml.AllocatableReader;
import org.rapla.storage.xml.AllocatableWriter;
import org.rapla.storage.xml.CategoryReader;
import org.rapla.storage.xml.CategoryWriter;
import org.rapla.storage.xml.ConflictReader;
import org.rapla.storage.xml.ConflictWriter;
import org.rapla.storage.xml.DynamicTypeReader;
import org.rapla.storage.xml.DynamicTypeWriter;
import org.rapla.storage.xml.PeriodReader;
import org.rapla.storage.xml.PeriodWriter;
import org.rapla.storage.xml.PreferenceReader;
import org.rapla.storage.xml.PreferenceWriter;
import org.rapla.storage.xml.RaplaCalendarSettingsReader;
import org.rapla.storage.xml.RaplaCalendarSettingsWriter;
import org.rapla.storage.xml.RaplaConfigurationReader;
import org.rapla.storage.xml.RaplaConfigurationWriter;
import org.rapla.storage.xml.RaplaMapReader;
import org.rapla.storage.xml.RaplaMapWriter;
import org.rapla.storage.xml.RaplaXMLReader;
import org.rapla.storage.xml.RaplaXMLWriter;
import org.rapla.storage.xml.ReferenceReader;
import org.rapla.storage.xml.RemoveReader;
import org.rapla.storage.xml.ReservationReader;
import org.rapla.storage.xml.ReservationWriter;
import org.rapla.storage.xml.StoreReader;
import org.rapla.storage.xml.UserReader;
import org.rapla.storage.xml.UserWriter;

public class IOContext
{
   protected Map<String,RaplaType> getLocalnameMap()  {
        //WARNING We can't use RaplaType.getRegisteredTypes() because the class could not be registered on load time
        Map<String,RaplaType> localnameMap = new HashMap<String,RaplaType>();
        localnameMap.put( Reservation.TYPE.getLocalName(), Reservation.TYPE);
        localnameMap.put( Appointment.TYPE.getLocalName(), Appointment.TYPE);
        localnameMap.put( Allocatable.TYPE.getLocalName(), Allocatable.TYPE);
        localnameMap.put( User.TYPE.getLocalName(), User.TYPE);
        localnameMap.put( Preferences.TYPE.getLocalName(), Preferences.TYPE);
        localnameMap.put( Period.TYPE.getLocalName(), Period.TYPE);
        localnameMap.put( Category.TYPE.getLocalName(), Category.TYPE);
        localnameMap.put( DynamicType.TYPE.getLocalName(), DynamicType.TYPE);
        localnameMap.put( Attribute.TYPE.getLocalName(), Attribute.TYPE);
        localnameMap.put( RaplaConfiguration.TYPE.getLocalName(), RaplaConfiguration.TYPE);
        localnameMap.put( RaplaMap.TYPE.getLocalName(), RaplaMap.TYPE);
        localnameMap.put( CalendarModelConfiguration.TYPE.getLocalName(), CalendarModelConfiguration.TYPE);
        localnameMap.put( Conflict.TYPE.getLocalName(), Conflict.TYPE);
        return localnameMap;
    }
 
    protected void addReaders(Map<Object,RaplaXMLReader> readerMap,RaplaContext context) throws RaplaException {
        readerMap.put( "remove",new RemoveReader( context));
        readerMap.put( "reference",new ReferenceReader( context));
        readerMap.put( "store",new StoreReader( context));
        readerMap.put( Category.TYPE,new CategoryReader( context));
        readerMap.put( Preferences.TYPE, new PreferenceReader(context) );
        readerMap.put( DynamicType.TYPE, new DynamicTypeReader(context) );
        readerMap.put( User.TYPE, new UserReader(context));
        readerMap.put( Allocatable.TYPE, new AllocatableReader(context) );
        readerMap.put( Period.TYPE, new PeriodReader(context) );
        readerMap.put( Reservation.TYPE,new ReservationReader(context));
        readerMap.put( RaplaConfiguration.TYPE, new RaplaConfigurationReader(context));
        readerMap.put( RaplaMap.TYPE, new RaplaMapReader<Object>(context));
        readerMap.put( CalendarModelConfiguration.TYPE, new RaplaCalendarSettingsReader(context) );
        readerMap.put( Conflict.TYPE, new ConflictReader(context) );
    }

     protected void addWriters(Map<RaplaType,RaplaXMLWriter> writerMap,RaplaContext context) throws RaplaException {
        writerMap.put( Category.TYPE,new CategoryWriter(context));
        writerMap.put( Preferences.TYPE,new PreferenceWriter(context) );
        writerMap.put( DynamicType.TYPE,new DynamicTypeWriter(context));
        writerMap.put( User.TYPE, new UserWriter(context) );
        writerMap.put( Allocatable.TYPE, new AllocatableWriter(context) );
        writerMap.put( Period.TYPE, new PeriodWriter(context) );
        writerMap.put( Reservation.TYPE,new ReservationWriter(context));
        writerMap.put( RaplaConfiguration.TYPE,new RaplaConfigurationWriter(context) );
        writerMap.put( RaplaMap.TYPE, new RaplaMapWriter(context) );
        writerMap.put( Preferences.TYPE, new PreferenceWriter(context) );
        writerMap.put( CalendarModelConfiguration.TYPE, new RaplaCalendarSettingsWriter(context) );
        writerMap.put( Conflict.TYPE, new ConflictWriter(context) );
    }

    public RaplaDefaultContext createInputContext(RaplaContext parentContext, EntityStore store, IdTable idTable) throws RaplaException {
         
        RaplaDefaultContext ioContext = new RaplaDefaultContext( parentContext);
        ioContext.put(EntityStore.class, store);
        ioContext.put(IdTable.class,idTable);
        ioContext.put(PreferenceReader.LOCALNAMEMAPENTRY, getLocalnameMap());
        Map<Object,RaplaXMLReader> readerMap = new HashMap<Object,RaplaXMLReader>();
        ioContext.put(PreferenceReader.READERMAP, readerMap);
        addReaders( readerMap, ioContext);
        return ioContext;
     }
    public static TypedComponentRole<Boolean> IDONLY = new TypedComponentRole<Boolean>( IOContext.class.getName() + ".idonly");
    public static TypedComponentRole<Boolean> PRINTVERSIONS = new TypedComponentRole<Boolean>( IOContext.class.getName() + ".printversion");
    public static TypedComponentRole<Provider<Category>> SUPERCATEGORY = new TypedComponentRole<Provider<Category>>( IOContext.class.getName() + ".supercategory");
    
    public RaplaDefaultContext createOutputContext(RaplaContext parentContext, Provider<Category> superCategory,boolean includeIds, boolean includeVersions) throws RaplaException {
        
        RaplaDefaultContext ioContext = new RaplaDefaultContext( parentContext);
        if ( includeIds)
        {
            ioContext.put(IDONLY, Boolean.TRUE);
        }
        if ( includeVersions)
        {
            ioContext.put(PRINTVERSIONS, Boolean.TRUE);
        }
        if ( superCategory != null)
        {
        	ioContext.put( SUPERCATEGORY, superCategory);
        }
        ioContext.put(PreferenceReader.LOCALNAMEMAPENTRY, getLocalnameMap());
        Map<RaplaType,RaplaXMLWriter> writerMap = new HashMap<RaplaType,RaplaXMLWriter>();
        ioContext.put(PreferenceWriter.WRITERMAP, writerMap);
        addWriters( writerMap, ioContext );
        return ioContext;
     }
    
    
}
