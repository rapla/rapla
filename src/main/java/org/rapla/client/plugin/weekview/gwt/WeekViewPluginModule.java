package org.rapla.client.plugin.weekview.gwt;

import org.rapla.client.base.CalendarPlugin;
import org.rapla.client.plugin.weekview.CalendarWeekView;
import org.rapla.client.plugin.weekview.CalendarWeekViewPresenter;

import com.google.gwt.inject.client.GinModule;
import com.google.gwt.inject.client.binder.GinBinder;
import com.google.gwt.inject.client.multibindings.GinMultibinder;

public class WeekViewPluginModule implements GinModule {

    @Override
    public void configure(GinBinder binder) {
        GinMultibinder<CalendarPlugin> uriBinder = GinMultibinder.newSetBinder(binder, CalendarPlugin.class);
        uriBinder.addBinding().to(CalendarWeekViewPresenter.class);
        binder.bind(CalendarWeekView.class).to(CalendarWeekViewImpl.class);
    }

}
