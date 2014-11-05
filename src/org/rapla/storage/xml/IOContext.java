package org.rapla.storage.xml;

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
import org.rapla.storage.IdCreator;
import org.rapla.storage.impl.EntityStore;

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
 
    protected void addReaders(Map<RaplaType,RaplaXMLReader> readerMap,RaplaContext context) throws RaplaException {
        readerMap.put( Category.TYPE,new CategoryReader( context));
        readerMap.put( Conflict.TYPE,new ConflictReader( context));
        readerMap.put( Preferences.TYPE, new PreferenceReader(context) );
        readerMap.put( DynamicType.TYPE, new DynamicTypeReader(context) );
        readerMap.put( User.TYPE, new UserReader(context));
        readerMap.put( Allocatable.TYPE, new AllocatableReader(context) );
        readerMap.put( Period.TYPE, new PeriodReader(context) );
        readerMap.put( Reservation.TYPE,new ReservationReader(context));
        readerMap.put( RaplaConfiguration.TYPE, new RaplaConfigurationReader(context));
        readerMap.put( RaplaMap.TYPE, new RaplaMapReader(context));
        readerMap.put( CalendarModelConfiguration.TYPE, new RaplaCalendarSettingsReader(context) );
    }

     protected void addWriters(Map<RaplaType,RaplaXMLWriter> writerMap,RaplaContext context) throws RaplaException {
        writerMap.put( Category.TYPE,new CategoryWriter(context));
        writerMap.put( Preferences.TYPE,new PreferenceWriter(context) );
        writerMap.put( DynamicType.TYPE,new DynamicTypeWriter(context));
        writerMap.put( User.TYPE, new UserWriter(context) );
        writerMap.put( Allocatable.TYPE, new AllocatableWriter(context) );
        writerMap.put( Reservation.TYPE,new ReservationWriter(context));
        writerMap.put( RaplaConfiguration.TYPE,new RaplaConfigurationWriter(context) );
        writerMap.put( RaplaMap.TYPE, new RaplaMapWriter(context) );
        writerMap.put( Preferences.TYPE, new PreferenceWriter(context) );
        writerMap.put( CalendarModelConfiguration.TYPE, new RaplaCalendarSettingsWriter(context) );
    }

    public RaplaDefaultContext createInputContext(RaplaContext parentContext, EntityStore store, IdCreator idTable) throws RaplaException {
         
        RaplaDefaultContext ioContext = new RaplaDefaultContext( parentContext);
        ioContext.put(EntityStore.class, store);
        ioContext.put(IdCreator.class,idTable);
        ioContext.put(PreferenceReader.LOCALNAMEMAPENTRY, getLocalnameMap());
        Map<RaplaType,RaplaXMLReader> readerMap = new HashMap<RaplaType,RaplaXMLReader>();
        ioContext.put(PreferenceReader.READERMAP, readerMap);
        addReaders( readerMap, ioContext);
        return ioContext;
     }
    public static TypedComponentRole<Boolean> PRINTID = new TypedComponentRole<Boolean>( IOContext.class.getName() + ".idonly");
    public static TypedComponentRole<Provider<Category>> SUPERCATEGORY = new TypedComponentRole<Provider<Category>>( IOContext.class.getName() + ".supercategory");
    
    public RaplaDefaultContext createOutputContext(RaplaContext parentContext, Provider<Category> superCategory,boolean includeIds) throws RaplaException {
        
        RaplaDefaultContext ioContext = new RaplaDefaultContext( parentContext);
        if ( includeIds)
        {
            ioContext.put(PRINTID, Boolean.TRUE);
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
