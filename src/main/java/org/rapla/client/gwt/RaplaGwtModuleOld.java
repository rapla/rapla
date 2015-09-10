package org.rapla.client.gwt;

import com.google.gwt.inject.client.GinModule;
import com.google.gwt.inject.client.binder.GinBinder;
import com.google.gwt.inject.client.multibindings.GinMultibinder;
import org.rapla.AppointmentFormaterImpl;
import org.rapla.client.*;
import org.rapla.client.base.CalendarPlugin;
import org.rapla.client.edit.reservation.sample.ReservationPresenter;
import org.rapla.client.gui.menu.MenuView;
import org.rapla.client.gui.menu.gwt.MenuViewImpl;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.client.ClientBundleManager;
import org.rapla.components.util.CommandScheduler;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.facade.CalendarOptions;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.internal.CalendarOptionsImpl;
import org.rapla.facade.internal.FacadeImpl;
import org.rapla.framework.RaplaLocale;
import org.rapla.gui.ReservationController;
import org.rapla.gui.internal.edit.reservation.ReservationEditFactory;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.dbrm.RemoteConnectionInfo;
import org.rapla.storage.dbrm.RemoteOperator;

import javax.inject.Singleton;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class RaplaGwtModuleOld implements GinModule{

    public final PrintWriter asd;
    RaplaGwtModuleOld() throws FileNotFoundException
    {
        asd = new PrintWriter(new FileOutputStream(new File("D:/asd.log")));
        asd.write("Init ");
    }
    @Override
    public void configure(GinBinder binder) {
        try
        {
            asd.write("configure");
            asd.flush();
            final ClassLoader cl = new ClassLoader(ClassLoader.getSystemClassLoader()){
                @Override public Class<?> loadClass(String name) throws ClassNotFoundException
                {
                    final Class<?> aClass = super.loadClass(name, true);
                    asd.write("\n" + aClass + "\n");
                    asd.write("Loaded");
                    return aClass;
                }
            };
            final Class<?> aClass = cl.loadClass("org.rapla.client.gwt.111");
            asd.write("loaded" + aClass.toString());
            //Constructor c = null;1
            //GinModule o = (GinModule) c.newInstance();
            //o.configure(binder);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e.getMessage(), e);
        }
        finally
        {
            asd.close();
        }
    if(true)throw new IllegalStateException("Stopp gwt 2");
        binder.bind( RaplaLocale.class).to(GwtRaplaLocale.class).in(Singleton.class);
        binder.bind( BundleManager.class).to(ClientBundleManager.class).in(Singleton.class);
        binder.bind( CommandScheduler.class).to(GwtCommandScheduler.class).in(Singleton.class);
        binder.bind( AppointmentFormater.class).to(AppointmentFormaterImpl.class).in(Singleton.class);
        binder.bind( ClientFacade.class).to(FacadeImpl.class).in(Singleton.class);
        binder.bind( CalendarOptions.class).to(CalendarOptionsImpl.class).in(Singleton.class);
        binder.bind( StorageOperator.class).to(RemoteOperator.class).in(Singleton.class);
        binder.bind( ApplicationView.class).to(ApplicationViewImpl.class).in(Singleton.class);;
        binder.bind( ActivityManager.class).to(GwtActivityManagerImpl.class).in(Singleton.class);
        binder.bind( ReservationController.class).to(ReservationControllerGwtImpl.class).in(Singleton.class);
        binder.bind( ReservationEditFactory.class).to(ReservationEditFactoryGwt.class).in(Singleton.class);;
        binder.bind(MenuView.class).to(MenuViewImpl.class).in(Singleton.class);
        binder.bind(CalendarPlaceView.class).to(CalendarPlaceViewImpl.class).in(Singleton.class);
        binder.bind(ResourceSelectionView.class).to(ResourceSelectionViewImpl.class).in(Singleton.class);

        {
            GinMultibinder<PlacePresenter> placeBinder = GinMultibinder.newSetBinder(binder, PlacePresenter.class);
            placeBinder.addBinding().to(CalendarPlacePresenter.class).in(Singleton.class);
        }
        {
            GinMultibinder<PlacePresenter> placeBinder = GinMultibinder.newSetBinder(binder, PlacePresenter.class);
            placeBinder.addBinding().to(ResourceSelectionPlace.class).in(Singleton.class);
        }

        {
            GinMultibinder<ActivityPresenter> activityBinder = GinMultibinder.newSetBinder(binder, ActivityPresenter.class);
            activityBinder.addBinding().to(ReservationPresenter.class).in(Singleton.class);
        }
    }

}

