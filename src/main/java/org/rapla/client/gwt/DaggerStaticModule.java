package org.rapla.client.gwt;

import java.util.Locale;
import java.util.Map;

import javax.inject.Provider;
import javax.inject.Singleton;

import org.rapla.client.ActivityManager;
import org.rapla.client.ActivityPresenter;
import org.rapla.client.Application;
import org.rapla.client.ApplicationView;
import org.rapla.client.PlacePresenter;
import org.rapla.components.i18n.BundleManager;
import org.rapla.entities.User;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.internal.CalendarModelImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.framework.logger.internal.RaplaJDKLoggingAdapterWithoutClassnameSupport;

import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;

import dagger.Module;
import dagger.Provides;

@Module
public class DaggerStaticModule
{

    @Provides
    @Singleton
    public Logger provideLogger()
    {
        return new RaplaJDKLoggingAdapterWithoutClassnameSupport().get();
    }

    @Provides
    @Singleton
    public EventBus provideEventBus()
    {
        return new SimpleEventBus();
    }

    @Provides
    @Singleton
    public CalendarSelectionModel provideCalendar(ClientFacade facade, RaplaLocale raplaLocale) throws RaplaException
    {
        Locale locale = raplaLocale.getLocale();
        User user = facade.getUser();
        CalendarModelImpl result = new CalendarModelImpl(locale, user, facade);
        result.load(null);
        return result;
    }
    
    @Provides
    @Singleton
    public Application provideApplication(final ApplicationView mainView, final EventBus eventBus, final Map<String, ActivityPresenter> activityPresenters, final Map<String, PlacePresenter> placePresenters, final Logger logger, final BundleManager bundleManager, final ClientFacade facade, final Provider<ActivityManager> activityManager)
    {
        return new Application(mainView, eventBus, activityPresenters, placePresenters, logger, bundleManager, facade, activityManager);
    }

}
