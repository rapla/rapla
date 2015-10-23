//package org.rapla.client.gwt;
//
//import com.google.gwt.inject.client.GinModule;
//import com.google.gwt.inject.client.binder.GinBinder;
//import com.google.inject.Provides;
//import com.google.web.bindery.event.shared.EventBus;
//import com.google.web.bindery.event.shared.SimpleEventBus;
//import org.rapla.entities.User;
//import org.rapla.facade.CalendarSelectionModel;
//import org.rapla.facade.ClientFacade;
//import org.rapla.facade.internal.CalendarModelImpl;
//import org.rapla.framework.RaplaException;
//import org.rapla.framework.RaplaLocale;
//import org.rapla.framework.logger.Logger;
//import org.rapla.framework.logger.internal.RaplaJDKLoggingAdapterWithoutClassnameSupport;
//
//import javax.inject.Singleton;
//import java.util.Locale;
//
//public class RaplaGwtExternalInjectionsModule  implements GinModule
//{
//    @Override public void configure(GinBinder binder)
//    {
//        binder.bind(Logger.class).toProvider(RaplaJDKLoggingAdapterWithoutClassnameSupport.class);
//        binder.bind(EventBus.class).to(SimpleEventBus.class).in(Singleton.class);
//    }
//
//    @Provides @Singleton public CalendarSelectionModel provideCalendar(ClientFacade facade, RaplaLocale raplaLocale) throws RaplaException
//    {
//        Locale locale = raplaLocale.getLocale();
//        User user = facade.getUser();
//        CalendarModelImpl result = new CalendarModelImpl(locale, user, facade);
//        result.load(null);
//        return result;
//    }
//}