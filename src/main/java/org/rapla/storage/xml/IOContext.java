package org.rapla.storage.xml;

import org.rapla.RaplaResources;
import org.rapla.entities.Category;
import org.rapla.entities.RaplaObject;
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
import org.rapla.entities.dynamictype.internal.KeyAndPathResolver;
import org.rapla.entities.storage.ImportExportEntity;
import org.rapla.facade.Conflict;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.TypedComponentRole;
import org.rapla.logger.Logger;
import org.rapla.storage.IdCreator;
import org.rapla.storage.impl.EntityStore;

import javax.inject.Provider;
import java.util.HashMap;
import java.util.Map;


public class IOContext
{
   protected Map<String,Class<? extends RaplaObject>> getLocalnameMap()  {
        //WARNING We can't use RaplaType.getRegisteredTypes() because the class could not be registered on load time
        Map<String,Class<? extends RaplaObject>> localnameMap = new HashMap<>();
        put( localnameMap, Reservation.class);
        put( localnameMap, Appointment.class);
        put( localnameMap, Allocatable.class);
        put( localnameMap, User.class);
       put( localnameMap, Preferences.class);
       put( localnameMap, Period.class);
       put( localnameMap, Category.class);
       put( localnameMap, DynamicType.class);
       put( localnameMap, Attribute.class);
       put( localnameMap, RaplaConfiguration.class);
       put( localnameMap, RaplaMap.class);
       put( localnameMap, CalendarModelConfiguration.class);
       put( localnameMap, Conflict.class);
        return localnameMap;
    }
    private void put(Map<String,Class<? extends RaplaObject>> map,Class<? extends RaplaObject> classType)
    {
        String localname = RaplaType.getLocalName( classType);
        map.put( localname, classType);
    }
 
    protected void addReaders(Map<Class<? extends RaplaObject>,RaplaXMLReader> readerMap,RaplaXMLContext context) throws RaplaException {
        readerMap.put( Category.class,new CategoryReader( context));
        readerMap.put( Conflict.class,new ConflictReader( context));
        readerMap.put( Preferences.class, new PreferenceReader(context) );
        readerMap.put( DynamicType.class, new DynamicTypeReader(context) );
        readerMap.put( User.class, new UserReader(context));
        readerMap.put( Allocatable.class, new AllocatableReader(context) );
        readerMap.put( Period.class, new PeriodReader(context) );
        readerMap.put( Reservation.class,new ReservationReader(context));
        readerMap.put( RaplaConfiguration.class, new RaplaConfigurationReader(context));
        readerMap.put( RaplaMap.class, new RaplaMapReader(context));
        readerMap.put( CalendarModelConfiguration.class, new RaplaCalendarSettingsReader(context) );
        readerMap.put( ImportExportEntity.class, new ImportExportReader(context) );
    }

     protected void addWriters(Map<Class<? extends RaplaObject>,RaplaXMLWriter> writerMap,RaplaXMLContext context) throws RaplaException {
        writerMap.put( Category.class,new CategoryWriter(context));
        writerMap.put( Preferences.class,new PreferenceWriter(context) );
        writerMap.put( DynamicType.class,new DynamicTypeWriter(context));
        writerMap.put( User.class, new UserWriter(context) );
        writerMap.put( Allocatable.class, new AllocatableWriter(context) );
        writerMap.put( Reservation.class,new ReservationWriter(context));
        writerMap.put( RaplaConfiguration.class,new RaplaConfigurationWriter(context) );
        writerMap.put( RaplaMap.class, new RaplaMapWriter(context) );
        writerMap.put( Preferences.class, new PreferenceWriter(context) );
        writerMap.put( CalendarModelConfiguration.class, new RaplaCalendarSettingsWriter(context) );
        writerMap.put( ImportExportEntity.class, new ImportExportWriter(context) );
    }

    public RaplaDefaultXMLContext createInputContext(Logger logger,RaplaLocale locale,RaplaResources i18n, EntityStore store, IdCreator idTable, Category superCategory) throws RaplaException {
         
        RaplaDefaultXMLContext ioContext = new RaplaDefaultXMLContext( );
        ioContext.put(RaplaResources.class, i18n);
        ioContext.put(RaplaLocale.class, locale);
        ioContext.put(EntityStore.class, store);
        ioContext.put(Category.class, superCategory);
        ioContext.put(IdCreator.class,idTable);
        ioContext.put(Logger.class, logger);
        ioContext.put(KeyAndPathResolver.class,new KeyAndPathResolver(store, superCategory));
        ioContext.put(PreferenceReader.LOCALNAMEMAPENTRY, getLocalnameMap());
        Map<Class<? extends  RaplaObject>,RaplaXMLReader> readerMap = new HashMap<>();
        ioContext.put(PreferenceReader.READERMAP, readerMap);
        addReaders( readerMap, ioContext);
        return ioContext;
     }
    public static TypedComponentRole<Boolean> PRINTID = new TypedComponentRole<>(IOContext.class.getName() + ".idonly");
    public static TypedComponentRole<Provider<Category>> SUPERCATEGORY = new TypedComponentRole<>(IOContext.class.getName() + ".supercategory");
    
    public RaplaDefaultXMLContext createOutputContext(Logger logger,RaplaLocale locale,RaplaResources i18n, Provider<Category> superCategory,boolean includeIds) throws RaplaException {
        
        RaplaDefaultXMLContext ioContext = new RaplaDefaultXMLContext( );
        ioContext.put(RaplaResources.class, i18n);
        ioContext.put(RaplaLocale.class, locale);
        ioContext.put( Logger.class,logger );
        if ( includeIds)
        {
            ioContext.put(PRINTID, Boolean.TRUE);
        }
        if ( superCategory != null)
        {
        	ioContext.put( SUPERCATEGORY, superCategory);
        }
        ioContext.put(PreferenceReader.LOCALNAMEMAPENTRY, getLocalnameMap());
        Map<Class<? extends  RaplaObject>,RaplaXMLWriter> writerMap = new HashMap<>();
        ioContext.put(PreferenceWriter.WRITERMAP, writerMap);
        addWriters( writerMap, ioContext );
        return ioContext;
     }
    
    
}
