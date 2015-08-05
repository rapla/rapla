package org.rapla.client.gwt;

import java.util.Locale;

import javax.inject.Singleton;

import org.rapla.AppointmentFormaterImpl;
import org.rapla.client.ActivityManager;
import org.rapla.client.ApplicationView;
import org.rapla.client.gui.menu.MenuPresenter;
import org.rapla.client.gui.menu.MenuView;
import org.rapla.client.gui.menu.gwt.MenuViewImpl;
import org.rapla.client.gui.menu.gwt.context.ContextCreator;
import org.rapla.components.util.CommandScheduler;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.User;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.facade.CalendarOptions;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.internal.CalendarModelImpl;
import org.rapla.facade.internal.CalendarOptionsImpl;
import org.rapla.facade.internal.FacadeImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.framework.logger.internal.RaplaJDKLoggingAdapterWithoutClassnameSupport;
import org.rapla.gui.ReservationController;
import org.rapla.gui.internal.edit.reservation.ReservationEditFactory;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.dbrm.RemoteConnectionInfo;
import org.rapla.storage.dbrm.RemoteOperator;

import com.google.gwt.inject.client.GinModule;
import com.google.gwt.inject.client.binder.GinBinder;
import com.google.inject.Provides;
import com.google.inject.name.Names;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;

public class RaplaGWTModule implements GinModule{
    @Override
    public void configure(GinBinder binder) {
        binder.bind(Logger.class).toProvider(RaplaJDKLoggingAdapterWithoutClassnameSupport.class);
        binder.bind(I18nBundle.class).annotatedWith(Names.named(RaplaComponent.RaplaResourcesId)).to(GWTSampleI18nBundle.class).in(Singleton.class);
        binder.bind( RaplaLocale.class).to(GWTRaplaLocale.class).in(Singleton.class);
        binder.bind( RemoteConnectionInfo.class).in(Singleton.class);
        binder.bind( CommandScheduler.class).to(GWTCommandScheduler.class).in(Singleton.class);
        binder.bind( AppointmentFormater.class).to(AppointmentFormaterImpl.class).in(Singleton.class);
        binder.bind( ClientFacade.class).to(FacadeImpl.class).in(Singleton.class);
        binder.bind( CalendarOptions.class).to(CalendarOptionsImpl.class).in(Singleton.class);
        binder.bind( StorageOperator.class).to(RemoteOperator.class).in(Singleton.class);
        binder.bind( EventBus.class).to( SimpleEventBus.class).in(Singleton.class);
        
        binder.bind( ApplicationView.class).to(ApplicationViewImpl.class);
        binder.bind( ActivityManager.class).to(ActivityManagerImpl.class).in(Singleton.class);
        binder.bind( ReservationController.class).to(ReservationControllerGWTImpl.class).in(Singleton.class);
        binder.bind( ReservationEditFactory.class).to(ReservationEditFactoryGWT.class).in(Singleton.class);;
        //binder.bind( CalendarSelectionModel.class).toProvider(provider)(CalendarModelImpl.class).in(Singleton.class);

        binder.bind(ContextCreator.class).in(Singleton.class);
        binder.bind(MenuPresenter.class).in(Singleton.class);
        binder.bind(MenuView.class).to(MenuViewImpl.class).in(Singleton.class);
    }
    
    @Provides
    @Singleton
    public CalendarSelectionModel provideCalendar(ClientFacade facade, RaplaLocale raplaLocale) throws RaplaException
    {
        Locale locale = raplaLocale.getLocale();
        User user = facade.getUser(); 
        CalendarModelImpl result = new CalendarModelImpl(locale, user, facade);
        result.load( null );
        return result;
    }
}

/*
interface Bundle
{
    Locale getLocale();   
}

interface DummyRes extends Bundle
{
    String title();
    String name();
    String address();
}

interface ResProvicer<T extends Bundle>
{
    T getBundle(Locale locale);
    T getBundle(User user);
}

class ClientService
{
    @Inject
    DummyRes res;
    
    void hello()
    {
        System.out.println( res.name());
    }
}

class ServerService
{
    @Inject
    ResProvicer<DummyRes> resProv;
    
    
    void hello(User user)
    {
        Locale locale = getUserLocale(user);
        DummyRes res = resProv.getBundle( locale);
        System.out.println( res.name());
    }
    
    
}
*/